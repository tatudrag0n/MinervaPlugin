from pathlib import Path
import runpy

runpy.run_path(str(Path(__file__).with_name('expand_field_items_and_sync_display.py')), run_name='__main__')
