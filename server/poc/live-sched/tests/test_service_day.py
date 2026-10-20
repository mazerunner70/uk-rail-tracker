"""STP resolution and G43975 overlay load (DATA-MODEL §2 / §6)."""

from __future__ import annotations

from pathlib import Path

from live_sched.cif_loader import load_lm_schedules
from live_sched.service_day import ScheduleWindow, pick_winner, resolve_service_days

FIXTURES = Path(__file__).resolve().parents[1] / "fixtures"


def test_overlay_beats_permanent_on_shared_day():
    permanent = ScheduleWindow(
        service_guid="SVC-EUSBHM2612131222",
        train_uid="G43975",
        date_runs_from="261213",
        date_runs_to="270509",
        days_run="0000001",
        stp_indicator="P",
    )
    overlay = ScheduleWindow(
        service_guid="SVC-EUSBHM2612131223",
        train_uid="G43975",
        date_runs_from="261213",
        date_runs_to="261213",
        days_run="0000001",
        stp_indicator="O",
    )
    guid, cancelled = pick_winner([permanent, overlay])
    assert cancelled is False
    assert guid == "SVC-EUSBHM2612131223"

    resolved = resolve_service_days([permanent, overlay])
    assert resolved[("G43975", "261213")] == ("SVC-EUSBHM2612131223", False)
    # Later Sundays in the permanent window keep the P schedule.
    assert ("G43975", "261220") in resolved
    assert resolved[("G43975", "261220")] == ("SVC-EUSBHM2612131222", False)


def test_cancellation_clears_service_day():
    permanent = ScheduleWindow(
        service_guid="SVC-CREBHM2605170925",
        train_uid="C16922",
        date_runs_from="260517",
        date_runs_to="260517",
        days_run="0000001",
        stp_indicator="P",
    )
    cancel = ScheduleWindow(
        service_guid="SVC-CREBHM260517CANCEL",
        train_uid="C16922",
        date_runs_from="260517",
        date_runs_to="260517",
        days_run="0000001",
        stp_indicator="C",
    )
    assert pick_winner([permanent, cancel]) == (None, True)


def test_load_g43975_permanent_and_overlay(repo):
    result = load_lm_schedules(FIXTURES / "g43975_overlay.mca", repo)
    assert set(result.service_guids) == {
        "SVC-EUSBHM2612131222",
        "SVC-EUSBHM2612131223",
    }
    journeys = {j.stp_indicator: j for j in repo.list_journeys()}
    assert journeys["P"].origin_std == "1222"
    assert journeys["O"].origin_std == "1223"
    assert journeys["P"].run_date == journeys["O"].run_date == "261213"

    day = repo.get_service_day("G43975", "261213")
    assert day is not None
    assert day.cancelled is False
    assert day.service_guid == "SVC-EUSBHM2612131223"

    resolved = repo.get_journey_by_uid_date("G43975", "261213")
    assert resolved is not None
    assert resolved.service_guid == "SVC-EUSBHM2612131223"
    assert resolved.origin_std == "1223"
