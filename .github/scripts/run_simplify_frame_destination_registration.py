from pathlib import Path
import runpy

runpy.run_path(str(Path('.github/scripts/simplify_frame_destination_registration.py')), run_name='__main__')
