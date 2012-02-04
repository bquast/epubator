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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class FileChooser extends ListActivity {
//	private ArrayList<HashMap<String,String>> fileList;
	private	String path = "/";
	private	String filter = "";
	private ListView lv;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.filechoosermain);
		
		// Get extras
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			if (extras.containsKey("path")) {
				File pathFile = new File(extras.getString("path"));
				if (pathFile.exists() && pathFile.isDirectory() && pathFile.canRead()) {
					path = pathFile.getPath();
					if (!path.endsWith("/")) {
						path += "/";
					}
				}
			}
			if (extras.containsKey("filter")) {
				filter = extras.getString("filter");
			}
		}

		// Set up ListView
		lv = getListView();
		lv.setTextFilterEnabled(true);
		lv.setOnItemClickListener(new OnItemClickListener() {
			// New OnItemClickListener
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				String chosen = ((TextView) view.findViewById(R.id.name)).getText().toString();
				
				if(chosen.endsWith("/")) {
					// Change path
					if (chosen == "/") {
						path = chosen;
					} else if (chosen == "../") {
						path = path.substring(0, path.lastIndexOf('/', path.length() - 2) + 1);
					} else {
						path += chosen;
					}
					setFileList(path, filter);
				} else {
					// File chosen
					setResult(RESULT_OK, (new Intent()).setAction(path + chosen));
					finish();
				}
			}
		});

		// Show FileChooser
		setFileList(path, filter);
	}

	// Fill FileChooser
	private void setFileList(String dirPath, String ext) {
		ArrayList<HashMap<String,String>> fileList = new ArrayList<HashMap<String,String>>();
		HashMap<String,String> item;
		File f = new File(dirPath + "/");

		// Add Root & Up
		if (dirPath.length() > 1) {
			item = new HashMap<String,String>();
			item.put("filename", "/");
			item.put("detail", getResources().getString(R.string.root));
			fileList.add(item);
			item = new HashMap<String,String>();
			item.put("filename", "../");
			item.put("detail", getResources().getString(R.string.up));
			fileList.add(item);
		}

		// Add filenames
		File[] files = f.listFiles(); 
		if (files.length > 0) {
			Arrays.sort(files);
			for(File file : files) {
				if((!file.isHidden()) && (file.canRead())) {
					String fileName = file.getName();
					String date = DateUtils.formatDateTime(this, file.lastModified(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NUMERIC_DATE);
					String time = DateUtils.formatDateTime(this, file.lastModified(), DateUtils.FORMAT_SHOW_TIME);
					if (file.isDirectory()) {
						item = new HashMap<String,String>();
						item.put("filename", fileName + "/");
						item.put("detail", String.format("%s %s      %s", date, time, getResources().getString(R.string.folder)));
						fileList.add(item);
					} else if (fileName.endsWith(ext)) {
						item = new HashMap<String,String>();
						item.put("filename", fileName);
						item.put("detail", String.format("%s %s      %d Byte", date, time, file.length()));
						fileList.add(item);
					}
				}
			}
		}

		// Update screen
		lv.clearTextFilter();
		((TextView) findViewById(R.id.path)).setText(String.format(getResources().getString(R.string.path), path));
		setListAdapter(new SimpleAdapter(this, fileList, R.layout.filechooserrow, new String[] {"filename", "detail"}, new int[] {R.id.name, R.id.datesize}));
	}
}