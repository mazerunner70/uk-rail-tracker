"""CFA apply: sequence gate, N/R/D counts, atomic refuse (FR2–FR4)."""

from __future__ import annotations

import pytest

from live_sched.cfa_apply import apply_cfa_bytes, process_watch_folder
from live_sched.feed_sequence import get_feed_sequence, set_feed_sequence
from live_sched.repository import CallingPoint, Journey
from tests.cfa_fixtures import (
    BS_DELETE_LM,
    BS_NEW_LM,
    BS_REVISE_LM,
    make_cfa,
    write_cfa_zip,
)


def _seed_journey(repo):
    journey = Journey(
        service_guid="SVC-CREBHM2605170925",
        train_uid="C16922",
        run_date="260517",
        origin_crs="CRE",
        dest_crs="BHM",
        origin_std="0925",
        toc="LM",
        date_runs_to="261206",
        days_run="0000001",
        stp_indicator="P",
    )
    repo.upsert_journey(journey)
    repo.replace_calling_points(
        journey.service_guid,
        [
            CallingPoint(
                service_guid=journey.service_guid,
                seq=0,
                tiploc="CREWE",
                crs="CRE",
                public_arr=None,
                public_dep="0925",
                activity="origin",
            ),
            CallingPoint(
                service_guid=journey.service_guid,
                seq=1,
                tiploc="BHAMNWS",
                crs="BHM",
                public_arr="1025",
                public_dep=None,
                activity="destination",
            ),
        ],
    )


def test_accept_991_after_seed_990(repo, db_conn):
    assert get_feed_sequence(db_conn) == "990"
    text = make_cfa("991", BS_NEW_LM)
    seq, counts = apply_cfa_bytes(text.encode(), repo)
    assert seq == "991"
    assert counts.new == 1
    assert counts.total == 1
    assert get_feed_sequence(db_conn) == "991"
    assert repo.get_journey_by_cif_identity("C16922", "260517", "P") is not None


def test_refuse_wrong_sequence_leaves_db(repo, db_conn):
    _seed_journey(repo)
    before = get_feed_sequence(db_conn)
    text = make_cfa("995", BS_NEW_LM)
    with pytest.raises(ValueError, match="sequence refuse"):
        apply_cfa_bytes(text.encode(), repo)
    assert get_feed_sequence(db_conn) == before
    assert repo.get_journey("SVC-CREBHM2605170925") is not None


def test_duplicate_new_when_identity_exists(repo, db_conn):
    _seed_journey(repo)
    set_feed_sequence(db_conn, "990")
    text = make_cfa("991", BS_NEW_LM)
    _, counts = apply_cfa_bytes(text.encode(), repo)
    assert counts.duplicate == 1
    assert counts.new == 0


def test_revise_counts_override(repo, db_conn):
    _seed_journey(repo)
    set_feed_sequence(db_conn, "990")
    text = make_cfa("991", BS_REVISE_LM)
    _, counts = apply_cfa_bytes(text.encode(), repo)
    assert counts.override == 1
    revised = repo.get_journey_by_cif_identity("C16922", "260517", "P")
    assert revised is not None
    assert revised.origin_std == "0930"


def test_new_resolves_crs_from_stored_calling_points(repo, db_conn):
    """A daily CFA has no TI records; CRS comes from calling points already stored."""
    _seed_journey(repo)
    set_feed_sequence(db_conn, "990")
    lines = [
        "BSNC169992605172612060000001 PXX1G453012122300000 EMU506 110      S S          P",
        "BX         LMYLM301200",
        "LOCREWE   0925 09257  FL     TB",
        "LTBHAMNWS 1025 10255     TF",
    ]
    text = make_cfa("991", lines, tip_lines=[])
    _, counts = apply_cfa_bytes(text.encode(), repo)
    assert counts.new == 1
    assert counts.total == 1
    assert repo.get_journey_by_cif_identity("C16999", "260517", "P") is not None


def test_unmatched_delete_is_not_counted(repo, db_conn):
    set_feed_sequence(db_conn, "990")
    text = make_cfa("991", BS_DELETE_LM, tip_lines=[])
    _, counts = apply_cfa_bytes(text.encode(), repo)
    assert counts.deletes == 0
    assert counts.total == 0
    assert counts.new == 0


def test_delete_counts_deletes(repo, db_conn):
    _seed_journey(repo)
    set_feed_sequence(db_conn, "990")
    text = make_cfa("991", BS_DELETE_LM)
    _, counts = apply_cfa_bytes(text.encode(), repo)
    assert counts.deletes == 1
    assert counts.total == 1
    assert repo.get_journey_by_cif_identity("C16922", "260517", "P") is None


def test_wrap_999_to_000(repo, db_conn):
    set_feed_sequence(db_conn, "999")
    text = make_cfa("000", [])
    seq, counts = apply_cfa_bytes(text.encode(), repo)
    assert seq == "000"
    assert counts.total == 0
    assert get_feed_sequence(db_conn) == "000"


def test_member_name_sequence_when_hd_has_no_digits(repo, db_conn, tmp_path):
    import zipfile

    hd = (
        "HDTPS.UCFCATE.PD1107191907112108DFTTISD       FA190711300912" + " " * 20
    )[:80]
    zpath = tmp_path / "timetable_update_991.zip"
    with zipfile.ZipFile(zpath, "w") as zf:
        zf.writestr("RJTTC991CFA.txt", hd + "\nZZ\n")
        zf.writestr("RJTTC991DAT.txt", "associations\n")
    result = process_watch_folder(tmp_path, repo)
    assert result.exit_code == 0
    assert result.outcomes[0].status == "accepted"
    assert result.outcomes[0].sequence == "991"
    assert get_feed_sequence(db_conn) == "991"


def test_process_watch_folder_empty(repo, tmp_path):
    result = process_watch_folder(tmp_path, repo)
    assert result.exit_code == 0
    assert result.outcomes[0].status == "nothing"


def test_process_watch_folder_refuse_continues(repo, db_conn, tmp_path):
    write_cfa_zip(tmp_path / "a_bad.zip", "995", BS_NEW_LM)
    write_cfa_zip(tmp_path / "b_ok.zip", "991", BS_NEW_LM)
    # Lexicographic: a_bad first (refuse), then b_ok still attempted with expected 991
    result = process_watch_folder(tmp_path, repo)
    assert result.exit_code == 1
    assert result.outcomes[0].status == "refused"
    assert result.outcomes[1].status == "accepted"
    assert get_feed_sequence(db_conn) == "991"
    assert (tmp_path / "a_bad.zip").is_file()
    assert (tmp_path / "b_ok.zip").is_file()
