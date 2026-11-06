/** In-container trees this process may open. Host compose mounts `./mca` and `./db`. */
export const CONTAINER_MCA_DIR = "/mca";
export const CONTAINER_DB_DIR = "/db";
export const SQLITE_FILENAME = "timetable.sqlite";
export const NETWORK_SCHEMATIC_FILENAME = "network-schematic.json";
export const CONTAINER_SQLITE_PATH = `${CONTAINER_DB_DIR}/${SQLITE_FILENAME}`;
