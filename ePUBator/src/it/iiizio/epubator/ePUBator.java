/*
Copyright (C)2011 Ezio Querini <iiizio AT users.sf.net>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package it.iiizio.epubator;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class ePUBator extends Activity {
	String filename = "";
	static String path = "/sdcard/";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		Button bSelect = (Button) findViewById(R.id.select);
		bSelect.setOnClickListener(mSelectListener);
	}

	// Button pressed
	View.OnClickListener mSelectListener = new OnClickListener() {
		public void onClick(View v) {
			if (Convert.started()) {
				// Conversion already started, show progress
				if (Convert.working()) {
					Toast.makeText(getApplicationContext(), getResources().getString(R.string.cip), Toast.LENGTH_LONG).show();
				}
				startActivity(new Intent(ePUBator.this, Convert.class));
			} else {
				// Select a file
				Intent chooseFile = new Intent(ePUBator.this, FileChooser.class);
				chooseFile.putExtra("path", path);
				chooseFile.putExtra("filter", "pdf");
				startActivityForResult(chooseFile, 0);
			}
		}
	};

	// File selected, start conversion
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK) {
			filename = data.getAction();
			path = filename.substring(0, filename.lastIndexOf('/', filename.length()) + 1);

			Intent convert = new Intent(ePUBator.this, Convert.class);
			convert.putExtra("filename", filename);
			startActivity(convert);
		}
	}
}