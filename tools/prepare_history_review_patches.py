"""Produce review-only patches; never edit the app-owned files."""
from pathlib import Path
import difflib

root=Path(__file__).resolve().parents[1]
out=root/'docs/history-b-android/review-patches'
source=root/'app/src/main/java/dev/motioncare/app/MainHost.kt'
before=source.read_text(encoding='utf8')
after=before.replace('import dev.motioncare.feature.history.report.FinishedContent','import dev.motioncare.feature.history.report.FinishedContent\nimport dev.motioncare.feature.history.report.FinishedRoute')
after=after.replace('finished != null -> FinishedContent(', 'finished != null -> FinishedRoute(\n                                tripId = finished,\n                                trips = trips,\n                                profiles = profiles,')
relative=source.relative_to(root).as_posix()
(out/'app-finished-summary.patch').write_text(''.join(difflib.unified_diff(before.splitlines(True),after.splitlines(True),fromfile='a/'+relative,tofile='b/'+relative)),encoding='utf8')
test=out/'HistoryBFlowTest.kt'
relative='app/src/androidTest/java/dev/motioncare/app/HistoryBFlowTest.kt'
(out/'app-history-b-ui-test.patch').write_text(''.join(difflib.unified_diff([],test.read_text(encoding='utf8').splitlines(True),fromfile='/dev/null',tofile='b/'+relative)),encoding='utf8')
print('Prepared app-owned patches for review; app source is unchanged')
