const fs = require("fs");
const path = require("path");
const vm = require("vm");

const root = path.resolve(__dirname, "..");
const journeyPath = path.join(
  root,
  "docs",
  "design-archive",
  "history-b-2026-11-07",
  "ui-review",
  "selected-b",
  "journey-data.js",
);
const fixturePath = path.join(
  root,
  "feature",
  "history",
  "src",
  "debug",
  "assets",
  "history-b-synthetic.json",
);

const context = { document: { addEventListener() {} } };
vm.createContext(context);
vm.runInContext(
  fs.readFileSync(journeyPath, "utf8") +
    "\nthis.buildJourneyReplay = buildJourneyReplay;",
  context,
  { filename: journeyPath },
);

const fixture = JSON.parse(fs.readFileSync(fixturePath, "utf8"));
const stopCounts = [4, 6, 7];
const startCounts = [2, 3, 3];
const repeatCounts = [1, 2, 2];

fixture.source = "Approved B HTML deterministic replay; speed-derived events only";
fixture.trips = fixture.trips.map((trip, index) => ({
  id: trip.id,
  started: trip.started,
  duration: trip.duration,
  replay: context.buildJourneyReplay(
    trip.duration,
    stopCounts[index],
    startCounts[index],
    index + 3,
    repeatCounts[index],
  ),
}));

fs.writeFileSync(fixturePath, JSON.stringify(fixture) + "\n", "utf8");
console.log(
  fixture.trips
    .map(
      (trip) =>
        `${trip.id}: stop=${trip.replay.stopEvents.length}, start=${trip.replay.startEvents.length}, repeat=${trip.replay.repeatEvents.length}, samples=${trip.replay.samples.length}`,
    )
    .join("\n"),
);
