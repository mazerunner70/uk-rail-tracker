import * as esbuild from "esbuild";
import { mkdirSync, cpSync, existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(fileURLToPath(import.meta.url));
const dist = join(root, "dist");
mkdirSync(dist, { recursive: true });

await esbuild.build({
  entryPoints: [join(root, "src/main.ts")],
  outfile: join(dist, "bundle.js"),
  bundle: true,
  format: "esm",
  target: "es2022",
  sourcemap: true,
});

cpSync(join(root, "public/index.html"), join(dist, "index.html"));
if (existsSync(join(root, "public/style.css"))) {
  cpSync(join(root, "public/style.css"), join(dist, "style.css"));
}
