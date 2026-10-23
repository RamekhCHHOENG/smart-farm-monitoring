// Joins the scenes recorded by record-demo.mjs into one 1280x720 MP4, with the voice-over
// (OUT/cues.json) mixed in at the moment each caption appeared. Phone scenes are centred.
//   node scripts/make-demo-video.mjs <folder from record-demo.mjs> <output.mp4>
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const [IN, OUT] = process.argv.slice(2);
if (!IN || !OUT) {
  console.error("Usage: node scripts/make-demo-video.mjs <recorded folder> <output.mp4>");
  process.exit(2);
}
const cues = fs.existsSync(`${IN}/cues.json`) ? JSON.parse(fs.readFileSync(`${IN}/cues.json`, "utf8")) : [];
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "demo-"));
const ffmpeg = (args) => execFileSync("ffmpeg", ["-loglevel", "error", "-y", ...args], { stdio: "inherit" });
const duration = (file) => Number(execFileSync("ffprobe", ["-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", file]).toString());

const scenes = fs.readdirSync(IN).filter((d) => d.startsWith("raw-")).sort();
const parts = [];
scenes.forEach((dir, i) => {
  const sceneNo = i + 1;
  const webm = fs.readdirSync(`${IN}/${dir}`).find((f) => f.endsWith(".webm"));
  const video = `${IN}/${dir}/${webm}`;
  const seconds = duration(video);
  const mine = cues.filter((c) => c.scene === sceneNo);
  const inputs = ["-i", video];
  const filters = ["[0:v]scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2:color=#14231B,fps=30,format=yuv420p[v]"];
  if (mine.length) {
    mine.forEach((c, k) => {
      inputs.push("-i", c.file);
      filters.push(`[${k + 1}:a]aresample=48000,adelay=${c.offsetMs}|${c.offsetMs}[a${k}]`);
    });
    filters.push(`${mine.map((_, k) => `[a${k}]`).join("")}amix=inputs=${mine.length}:normalize=0,aformat=channel_layouts=stereo,apad[a]`);
  } else {
    inputs.push("-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo");
    filters.push("[1:a]anull[a]");
  }
  const part = `${tmp}/${String(sceneNo).padStart(2, "0")}.mp4`;
  ffmpeg([...inputs, "-filter_complex", filters.join(";"), "-map", "[v]", "-map", "[a]", "-t", seconds.toFixed(3),
    "-c:v", "libx264", "-preset", "medium", "-crf", "23", "-c:a", "aac", "-b:a", "160k", "-ar", "48000", part]);
  parts.push(part);
});
fs.writeFileSync(`${tmp}/list.txt`, parts.map((p) => `file '${p}'`).join("\n"));
ffmpeg(["-f", "concat", "-safe", "0", "-i", `${tmp}/list.txt`, "-c", "copy", "-movflags", "+faststart", OUT]);
fs.rmSync(tmp, { recursive: true, force: true });
console.log(`Wrote ${OUT} (${duration(OUT).toFixed(0)} s, ${cues.length} spoken cues)`);
