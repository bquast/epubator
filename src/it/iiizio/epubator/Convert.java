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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

public class Convert extends Activity {
	private static StringBuilder progressSb;
	private static ScrollView progressSv;
	private static TextView progressTv;
	private static Button okBt;
	private static Button stopBt;
	private static boolean okBtEnabled = true;
	public static boolean conversionStarted = false;
	private static boolean notificationSent = false;
	private static int result;
	private static String filename = "";
	private static String path = "";
	private static String tempPath = "";
	private static String pdfFilename = "";
	private static String epubFilename = "";
	private static String oldFilename = "";
	private static String tempFilename = "";
	private static String cover_file = "";

	private boolean includeImages;
	private boolean repeatedImages;
	private int pagesPerFile;
	private int onError;
	private boolean addMarkers;
	private boolean hideNotifi;
	private boolean tocFromPdf;
	private boolean logoOnCover;
	private boolean downloadDir;

	private final String PDF_EXT = ".pdf";
	private final String EPUB_EXT = " - ePUBator.epub";
	private final String OLD_EXT = " - ePUBator.old";
	private final String TEMP_EXT = " - ePUBator.tmp";

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_PROGRESS);
		setProgressBarVisibility(true);
		setContentView(R.layout.progressview);
		setProgress(0);

		// Set variables
		progressSv = (ScrollView)findViewById(R.id.scroll);
		progressTv = (TextView)findViewById(R.id.progress);
		okBt = (Button)findViewById(R.id.ok);
		okBt.setOnClickListener(mOkListener);
		stopBt = (Button)findViewById(R.id.stop);
		stopBt.setOnClickListener(mStopListener);

		getPrefs();

		if (conversionStarted) {
			// Update screen
			progressTv.setText(progressSb);
			setButtons(okBtEnabled);
		} else if (!notificationSent) {
			// Get filename
			Bundle extras = getIntent().getExtras();
			if (extras != null) {
				if (extras.containsKey("cover")) {
					cover_file = extras.getString("cover");
				} else {
					cover_file = "";
				}
				if (extras.containsKey("filename")) {
					pdfFilename = extras.getString("filename");
					String noExt = pdfFilename.substring(0, pdfFilename.lastIndexOf(PDF_EXT));
					path = noExt.substring(0, noExt.lastIndexOf('/', noExt.length()) + 1);
					filename = noExt.substring(noExt.lastIndexOf("/") + 1, noExt.length());

					// Check writable
					boolean writable = false;
					try {
						File checkFile = new File(path + TEMP_EXT);
						writable = checkFile.createNewFile();
						checkFile.delete();
					} catch (IOException e) {
					}
										
					// Save ePUB in the Download folder as user choice or if PDF folder is not writable
					if (downloadDir || !writable) {
						epubFilename = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() + "/" + filename + EPUB_EXT;
					} else {
						epubFilename = noExt + EPUB_EXT;
					}
					
					tempPath = getExternalCacheDir() + "/";
					oldFilename = tempPath + filename+ OLD_EXT;
					tempFilename = tempPath + filename + TEMP_EXT;

					new convertTask().execute();
				}
			}
		}

		// Remove notification
		((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(R.string.app_name);
		notificationSent = false;
	}

	@Override
	public void onResume() {
		super.onResume();

		getPrefs();
	}
	
	// Get preferences
	private void getPrefs() {
		SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(this);

		includeImages = prefs.getBoolean("include_images", true);
		repeatedImages = prefs.getBoolean("repeated_images", false);
		pagesPerFile = Integer.parseInt(prefs.getString("page_per_file", "5"));
		onError = Integer.parseInt(prefs.getString("on_error", "0"));
		addMarkers = prefs.getBoolean("add_markers", true);
		hideNotifi = prefs.getBoolean("hide_notifi", false);
		tocFromPdf = prefs.getBoolean("toc_from_pdf", true);
		logoOnCover = prefs.getBoolean("logo_on_cover", true);
		downloadDir = prefs.getBoolean("download_dir", false);
	}

	// Set buttons state
	private void setButtons(boolean flag) {
		okBtEnabled = flag;
		okBt.setEnabled(okBtEnabled);
		stopBt.setEnabled(!okBtEnabled);
	}

	// Ok button pressed
	private OnClickListener mOkListener = new OnClickListener()
	{
		public void onClick(View v)
		{
			conversionStarted = false;
			progressSb = null;
			progressSv = null;
			progressTv = null;
			finish();
		}
	};

	// Stop button pressed
	private OnClickListener mStopListener = new OnClickListener()
	{
		public void onClick(View v)
		{
			result = 5;
		}
	};

	// Back button pressed
	@Override
	public void onBackPressed() {
		conversionStarted = working();
		finish();
	}

	// Conversion in progress?
	public static boolean working() {
		return !okBtEnabled;
	}

	// Conversion started?
	public static boolean started() {
		return conversionStarted;
	}

	// Keep file dialog
	@Override
	protected Dialog onCreateDialog(int id) {
		if (id == 0) {
			// Build dialog
			return new AlertDialog.Builder(Convert.this)
			.setTitle(getResources().getString(R.string.extraction_error))
			.setMessage(getResources().getString(R.string.keep))
			// Ok action
			.setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					keepEpub();
				}
			})
			// Cancel action
			.setNegativeButton(getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					progressSb.append("\n" + getResources().getStringArray(R.array.message)[4] + "\n");
					deleteTmp();
				}
			})
			// Preview action
			.setNeutralButton(getResources().getString(R.string.verify), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					Intent verify = new Intent(getBaseContext(), Verify.class);
					verify.putExtra("filename", tempFilename);
					startActivityForResult(verify, 0);
				}
			})
			.create();
		} else
			return null;
	}

	// Show dialog again after preview activity
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		showDialog(0);
	}

	// Delete file
	private void deleteTmp() {
		new File(tempFilename).delete();
		if (new File(oldFilename).exists()) {
			new File(oldFilename).renameTo(new File(epubFilename));
			progressSb.append(getResources().getString(R.string.kept_old));
		} else {
			progressSb.append(getResources().getString(R.string.deleted));
		}
		progressTv.setText(progressSb);
		scroll_up();
	}

	// Keep file
	private void keepEpub() {
		progressSb.append("\n" + getResources().getStringArray(R.array.message)[0] + "\n");
		if (addMarkers) {
			String pageNumberString = String.format(getResources().getString(R.string.pagenumber), ">>\n");
			progressSb.append(String.format(getResources().getString(R.string.errors), "<<@") + pageNumberString);
			progressSb.append(String.format(getResources().getString(R.string.lost_pages), "<<#") + pageNumberString);
		}
		renameFile();
		progressSb.append(String.format(getResources().getString(R.string.epubfile), epubFilename));
		progressTv.setText(progressSb);
		scroll_up();
	}

	// Rename tmp file
	private void renameFile() {
		new File(tempFilename).renameTo(new File(epubFilename));
		new File(oldFilename).delete();
	}

	// Scroll scroll view up
	private void scroll_up() {
		progressSv.post(new Runnable() {
			public void run() {
				progressSv.fullScroll(ScrollView.FOCUS_DOWN);
			}
		});
	}

	// Send notification
	public void sendNotification() {
		if (!hideNotifi) {
			NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
			PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, Convert.class), 0);
			String message = getResources().getStringArray(R.array.message)[result];
			String tickerText = getResources().getString(R.string.app_name);
			Notification notif = new Notification(R.drawable.ic_launcher, message, System.currentTimeMillis());
			notif.setLatestEventInfo(this, tickerText, message, contentIntent);
			nm.notify(R.string.app_name, notif);
			notificationSent = true;
		}
	}

	// Start background task
	private class convertTask extends AsyncTask<Void, String, Void> {
		// Background task
		@Override
		protected Void doInBackground(Void... params) {
			// Remove cache files
			String[] files = new File(tempPath).list();
			if(files != null) {
				for (int i = 0; i < files.length; i++) {
					new File(tempPath + files[i]).delete();
				}
			}

			// Save old ePUB
			if (new File(epubFilename).exists()) {
				new File(epubFilename).renameTo(new File(oldFilename));
			}

			// Load PDF
			publishProgress(String.format(getResources().getString(R.string.load), pdfFilename));
			if (!(new File(pdfFilename).exists())) {
				// PDF file not found
				result = 1;
			} else if (ReadPdf.open(pdfFilename)) {
				// Failed to read PDF file
				result = 2;
			} else if (result != 5) {
				result = fillEpub();
			}

			return null;
		}

		// Update screen
		@Override
		protected void onProgressUpdate(String... messageArray) {
			for (String message : messageArray)
			{
				progressSb.append(message + "\n");
			}
			progressTv.setText(progressSb);
			scroll_up();
		}

		// Prepare background task
		@Override
		protected void onPreExecute() {
			progressSb = new StringBuilder();
			progressSb.append(getResources().getString(R.string.heading));
			progressSb.append(getResources().getString(R.string.library));
			setButtons(false);
			result = 0;
			conversionStarted = true;
		}

		// Background task ended
		@Override
		protected void onPostExecute(Void params) {
			if (result == 4) {
				if (onError == 0) {
					// Keep ePUB with errors
					keepEpub();
					result = 0;
				} else if (onError == 2){
					// Drop ePUB with errors
					deleteTmp();
				} else {
					// Ask for keeping ePUB with errors
					if (hasWindowFocus()) {
						showDialog(0);
					} else {
						keepEpub();
						result = 0;
					}
				}
			} else {
				// Delete on failure
				publishProgress("\n" + getResources().getStringArray(R.array.message)[result]);
				if (result > 0) {
					deleteTmp();
				} else {
					// Keep if ok
					renameFile();
					publishProgress(String.format(getResources().getString(R.string.epubfile), epubFilename));
				}
			}

			if (isFinishing()) {
				// Send notification
				sendNotification();
			}

			// Enable ok, disable stop
			setButtons(true);
		}

		// Fill ePUB file
		private int fillEpub() {
			try {
				// Stopped?
				if (result == 5) {
					return 5;
				}

				// Set up counter
				int pages = ReadPdf.getPages();
				publishProgress(String.format(getResources().getString(R.string.pages), pages));
				int totalFiles = 2 + pages;
				int writedFiles = 0;

				// Set flag
				boolean extractionErrorFlag = false;

				// Create ePUB file
				publishProgress(getResources().getString(R.string.create));
				if (WriteZip.create(tempFilename)) {
					return 3;
				}

				// Add required files
				publishProgress(getResources().getString(R.string.mimetype));
				setProgress(++writedFiles*9999/totalFiles);
				if (WriteZip.addText("mimetype", "application/epub+zip", true)) {
					return 3;
				}

				publishProgress(getResources().getString(R.string.container));
				if (WriteZip.addText("META-INF/container.xml", createContainer(), false)) {
					return 3;
				}

				String title = filename.replaceAll("[^\\p{Alnum}]", " ");
				String bookId = title + " - " + new Date().hashCode();
				
				publishProgress(getResources().getString(R.string.toc));
				if (WriteZip.addText("OEBPS/toc.ncx", createToc(pages, bookId, title), false)) {
					return 3;
				}

				// Add frontpage
				publishProgress(getResources().getString(R.string.frontpage));
				if (WriteZip.addText("OEBPS/frontpage.html", createFrontpage(), false)) {
					return 3;
				}

				publishProgress(getResources().getString(R.string.frontpagepng));
				if (createFrontpagePng()) {
					return 3;
				}

				// Add extracted text and images
				List<String> allImageList = new ArrayList<String>();
				for(int i = 1; i <= pages; i += pagesPerFile) {
					StringBuilder textSb = new StringBuilder();

					publishProgress(String.format(getResources().getString(R.string.html), i));
					int endPage = i + pagesPerFile - 1;
					if (endPage > pages) {
						endPage = pages;
					}

					for (int j = i; j <= endPage; j++) {
						// Stopped?
						if (result == 5) {
							return 5;
						}

						// Update progress bar
						setProgress(++writedFiles*9999/totalFiles);

						// Add anchor
						textSb.append("  <p>\n");
						textSb.append("  <a id=\"page" + j + "\"/>\n");
						
						// extract text
						String page = stringToHTMLString(ReadPdf.extractText(j));
						if (page.length() == 0) {
							publishProgress(String.format(getResources().getString(R.string.extraction_failure), j));
							extractionErrorFlag = true;
							if (addMarkers) {
								textSb.append("&lt;&lt;#" + j + "&gt;&gt;");
							}
						} else {
							if (page.matches(".*\\p{Cntrl}.*")) {
								extractionErrorFlag = true;
								if (addMarkers) {
									textSb.append(page.replaceAll("\\p{Cntrl}+", "&lt;&lt;@" + j + "&gt;&gt;"));
								} else {
									textSb.append(page.replaceAll("\\p{Cntrl}+", " "));
								}
							} else {
								textSb.append(page);
							}
						}

						// extract images
						if (includeImages) {
							List<String> imageList = ReadPdf.getImages(j);
							Iterator<String> iterator = imageList.iterator();
							while (iterator.hasNext()) {
								// Stopped?
								if (result == 5) {
									return 5;
								}

								String imageName = iterator.next();
								String imageTag = "\n<img alt=\"" + imageName + "\" src=\"" + imageName + "\" /><br/>";

								if (!allImageList.contains(imageName)) {
									allImageList.add(imageName);
									publishProgress(String.format(getResources().getString(R.string.image), imageName));
									textSb.append(imageTag);
								} else if (repeatedImages) {
									textSb.append(imageTag);
								}
							}
						}
						// Close page
						textSb.append("\n  </p>\n");
					}
					
					String text = textSb.toString();
					if (WriteZip.addText("OEBPS/page" + i + ".html", createHtml("page" + i, text.replaceAll("<br/>(?=[a-z])", "&nbsp;")) , false)) {
						return 3;
					}
				}

				// Add content.opf
				publishProgress(getResources().getString(R.string.content));
				setProgress(++writedFiles*9999/totalFiles);
				if (WriteZip.addText("OEBPS/content.opf", createContent(pages, bookId, allImageList, title), false)) {
					return 3;
				}

				// Close ePUB file
				publishProgress(getResources().getString(R.string.close));
				if (WriteZip.close()) {
					return 3;
				}

				if (extractionErrorFlag) {
					return 4;
				} else {
					return 0;
				}
			} catch(OutOfMemoryError e) {
				return 6;
			}
		}

		// Create container.xml
		private String createContainer() {
			StringBuilder container = new StringBuilder();
			container.append("<?xml version=\"1.0\"?>\n");
			container.append("<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n");
			container.append("   <rootfiles>\n");
			container.append("      <rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n");
			container.append("   </rootfiles>\n");
			container.append("</container>\n");
			return container.toString();
		}

		// Create content.opf
		private String createContent(int pages, String id, Iterable<String> images, String title) {
			StringBuilder content = new StringBuilder();
			content.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			content.append("<package xmlns=\"http://www.idpf.org/2007/opf\" unique-identifier=\"BookID\" version=\"2.0\">\n");
			content.append("    <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:opf=\"http://www.idpf.org/2007/opf\">\n");
			content.append("        <dc:title>" + title + "</dc:title>\n");
			content.append("        <dc:creator>" + (ReadPdf.getAuthor()).replaceAll("[<>]", "_") + "</dc:creator>\n");
			content.append("        <dc:creator opf:role=\"bkp\">ePUBator - Minimal offline PDF to ePUB converter for Android</dc:creator>\n");
			content.append("        <dc:identifier id=\"BookID\" opf:scheme=\"UUID\">" + id + "</dc:identifier>\n");
			content.append("        <dc:language>" + Resources.getSystem().getConfiguration().locale.getLanguage() + "</dc:language>\n");
			content.append("    </metadata>\n");
			content.append("    <manifest>\n");
			for(int i = 1; i <= pages; i += pagesPerFile) {
				content.append("        <item id=\"page" + i + "\" href=\"page" + i + ".html\" media-type=\"application/xhtml+xml\"/>\n");
			}
			content.append("        <item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n");
			content.append("        <item id=\"frontpage\" href=\"frontpage.html\" media-type=\"application/xhtml+xml\"/>\n");
			content.append("        <item id=\"cover\" href=\"frontpage.png\" media-type=\"image/png\"/>\n");
			for(String name : images) {
				content.append("        <item id=\"" + name + "\" href=\"" + name + "\" media-type=\"image/" + name.substring(name.lastIndexOf('.') + 1) + "\"/>\n");
			}
			content.append("    </manifest>\n");
			content.append("    <spine toc=\"ncx\">\n");
			content.append("        <itemref idref=\"frontpage\"/>\n");
			for(int i = 1; i <= pages; i += pagesPerFile) {
				content.append("        <itemref idref=\"page" + i + "\"/>\n");
			}
			content.append("    </spine>\n");
			content.append("    <guide>\n");
			content.append("        <reference type=\"cover\" title=\"Frontpage\" href=\"frontpage.html\"/>\n");
			content.append("    </guide>\n");
			content.append("</package>\n");
			return content.toString();
		}

		// Create toc.ncx
		private String createToc(int pages, String id, String title) {
			StringBuilder toc = new StringBuilder();
			toc.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			toc.append("<!DOCTYPE ncx PUBLIC \"-//NISO//DTD ncx 2005-1//EN\"\n");
			toc.append("   \"http://www.daisy.org/z3986/2005/ncx-2005-1.dtd\">\n");
			toc.append("<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">\n");
			toc.append("    <head>\n");
			toc.append("        <meta name=\"dtb:uid\" content=\"" + id + "\"/>\n");
			toc.append("        <meta name=\"dtb:depth\" content=\"1\"/>\n");
			toc.append("        <meta name=\"dtb:totalPageCount\" content=\"0\"/>\n");
			toc.append("        <meta name=\"dtb:maxPageNumber\" content=\"0\"/>\n");
			toc.append("    </head>\n");
			toc.append("    <docTitle>\n");
			toc.append("        <text>" + title + "</text>\n");
			toc.append("    </docTitle>\n");
			toc.append("    <navMap>\n");
			toc.append("        <navPoint id=\"navPoint-1\" playOrder=\"1\">\n");
			toc.append("            <navLabel>\n");
			toc.append("                <text>Frontpage</text>\n");
			toc.append("            </navLabel>\n");
			toc.append("            <content src=\"frontpage.html\"/>\n");
			toc.append("        </navPoint>\n");

			int playOrder = 2;
			boolean extractedToc = false;
			if (tocFromPdf) {
				// Try to extract toc from pdf
				XMLParser parser = new XMLParser();
				Document doc = parser.getDomElement(ReadPdf.getBookmarks());
				if (doc != null) {
					doc.normalize();
					NodeList nl = doc.getElementsByTagName("Title");
					if (nl != null) {
						int lastPage = Integer.MAX_VALUE;
						StringBuilder sb = new StringBuilder();
						// looping through all item nodes <item>
						for (int i = 0; i < nl.getLength(); i++) {
							Element e = (Element) nl.item(i);
							String action = parser.getValue(e, "Action");
							if (action.equals("GoTo")) {
								String chapter = parser.getElementValue(e).trim();
								try {
									int page = Integer.valueOf(parser.getValue(e, "Page").split(" ")[0]);
									
									// First entry not in page one, create a dummy one
									if ((lastPage == Integer.MAX_VALUE) && (page > 1))
									{
										sb.append(title);
										sb.append("\n");
										lastPage = 1;
									}
									
									// Add entry in toc
									if (page > lastPage) {
										int pageFile = ((int) ((lastPage - 1) / pagesPerFile)) * pagesPerFile + 1;
										toc.append("        <navPoint id=\"navPoint-" + playOrder + "\" playOrder=\"" + playOrder + "\">\n");
										toc.append("            <navLabel>\n");
										toc.append("                <text>" + sb.toString() + "                </text>\n");
										toc.append("            </navLabel>\n");
										toc.append("            <content src=\"page" + pageFile + ".html#page" + lastPage + "\"/>\n");
										toc.append("        </navPoint>\n");
										playOrder += 1;

										sb = new StringBuilder();
									}
									
									// Set next entry
									sb.append(chapter);
									sb.append("\n");
									lastPage = page;
								} catch (RuntimeException ex) {
									System.err.println("RuntimeException in xml extraction " + ex.getMessage());
								}
							}
							extractedToc = true;
						}
						
						// Add last entry
						if (sb.length() > 0) {
							int pageFile = ((int) ((lastPage - 1) / pagesPerFile)) * pagesPerFile + 1;
							toc.append("        <navPoint id=\"navPoint-" + playOrder + "\" playOrder=\"" + playOrder + "\">\n");
							toc.append("            <navLabel>\n");
							toc.append("                <text>" + sb.toString() + "                </text>\n");
							toc.append("            </navLabel>\n");
							toc.append("            <content src=\"page" + pageFile + ".html#page" + lastPage + "\"/>\n");
							toc.append("        </navPoint>\n");
						}

					}
				}
			}

			// Create dummy toc
			if (!extractedToc) {
				if(tocFromPdf) {
					publishProgress(getResources().getString(R.string.no_toc));
				}
				publishProgress(getResources().getString(R.string.dummy_toc));

				for(int i = 1; i <= pages; i += pagesPerFile) {
					toc.append("        <navPoint id=\"navPoint-" + playOrder + "\" playOrder=\"" + playOrder + "\">\n");
					toc.append("            <navLabel>\n");
					toc.append("                <text>Page" + i + "</text>\n");
					toc.append("            </navLabel>\n");
					toc.append("            <content src=\"page" + i + ".html\"/>\n");
					toc.append("        </navPoint>\n");
					playOrder += 1;
				}
			} else {
				publishProgress(getResources().getString(R.string.pdf_toc));
			}
			
			toc.append("    </navMap>\n");
			toc.append("</ncx>\n");
			return toc.toString();
		}

		// Create html
		private String createHtml(String title, String body) {
			StringBuilder html = new StringBuilder();
			html.append("  <!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \n");
			html.append("  \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n");
			html.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n");
			html.append("<head>\n");
			html.append("  <meta http-equiv=\"content-type\" content=\"text/html; charset=utf-8\"/>\n");
			html.append("  <meta name=\"generator\" content=\"ePUBator - Minimal offline PDF to ePUB converter for Android\"/>\n");
			html.append("  <title>" + title + "</title>\n");
			html.append("</head>\n");
			html.append("<body>\n");
			html.append(body);
			html.append("\n</body>\n");
			html.append("</html>\n");
			return html.toString();
		}

		// Create frontpage.html
		private String createFrontpage() {
			return createHtml("Frontpage", "<div><img width=\"100%\" alt=\"cover\" src=\"frontpage.png\" /></div>");
		}

		// Create frontpage.png
		private boolean createFrontpagePng() {
			final int maxWidth = 300;
			final int maxHeight = 410;
			final int border = 10;
			final int fontsize = 48;

			// Grey background
			Bitmap bmp = Bitmap.createBitmap(maxWidth, maxHeight, Bitmap.Config.RGB_565);
			Paint paint  = new Paint();
			paint.setColor(Color.LTGRAY);
			Canvas canvas = new Canvas(bmp);
			canvas.drawRect(0, 0, maxWidth, maxHeight, paint);
			
			// Load image
			Bitmap img = null;
			if (cover_file != "") {
				// Get dimensions
			    final BitmapFactory.Options options = new BitmapFactory.Options();
			    options.inJustDecodeBounds = true;
			    BitmapFactory.decodeFile(cover_file, options);

			    // Get image
			    options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight);
			    options.inJustDecodeBounds = false;
			    img = BitmapFactory.decodeFile(cover_file, options);
			}

			// Add image as cover
			if (img != null) {
				canvas.drawBitmap(img, null , new Rect(0, 0, maxWidth, maxHeight), new Paint(Paint.FILTER_BITMAP_FLAG));
				publishProgress(getResources().getString(R.string.imagecover));
			} else {
				// Add ePUBator logo
				if (logoOnCover) {
					img = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);
					canvas.drawBitmap(img, maxWidth - img.getWidth(), maxHeight - img.getHeight(), new Paint(Paint.FILTER_BITMAP_FLAG));
				}

				// Add title as cover
				paint.setTextSize(fontsize);
				paint.setColor(Color.BLACK);
				paint.setAntiAlias(true);
				paint.setStyle(Paint.Style.FILL);

				String title = filename.replaceAll("_", " ");
				String words[] = title.split("\\s");

				float newline = paint.getFontSpacing();
				float x = border;
				float y = newline;

				for (String word : words) {
					float len = paint.measureText(word + " ");

					// Line wrap
					if ((x > border) && ((x + len) > maxWidth)) {
						x = border;
						y += newline;
					}

					// Word wrap
					while ((x + len) > maxWidth) {
						int maxChar = (int) (word.length() * (maxWidth - border - x) / paint.measureText(word));
						canvas.drawText(word.substring(0, maxChar), x, y, paint);
						word = word.substring(maxChar);
						len = paint.measureText(word + " ");
						x = border;
						y += newline;
					}

					canvas.drawText(word, x, y, paint);
					x += len;
				}
				publishProgress(getResources().getString(R.string.titlecover));
			}

			// Save bmp as png
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			bmp.compress(Bitmap.CompressFormat.PNG, 100, baos);
			return WriteZip.addImage("OEBPS/frontpage.png", baos.toByteArray());
		}

		// calculateInSampleSize from Android Developers site
		// https://developer.android.com/training/displaying-bitmaps/load-bitmap.html
		public int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
			// Raw height and width of image
			final int height = options.outHeight;
			final int width = options.outWidth;
			int inSampleSize = 1;

			if (height > reqHeight || width > reqWidth) {

				final int halfHeight = height / 2;
				final int halfWidth = width / 2;

				// Calculate the largest inSampleSize value that is a power of 2 and keeps both
				// height and width larger than the requested height and width.
				while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
					inSampleSize *= 2;
				}
			}

			return inSampleSize;
		}
		
		//  stringToHTMLString found on the web, no license indicated
		//  http://www.rgagnon.com/javadetails/java-0306.html
		//	Author: S. Bayer.
		private  String stringToHTMLString(String string) {
			StringBuilder sb = new StringBuilder(); // changed StringBuffer to StringBuilder to prevent buffer overflow (iiizio)
			// true if last char was blank
			boolean lastWasBlankChar = false;
			int len = string.length();
			char c;

			for (int i = 0; i < len; i++)
			{
				c = string.charAt(i);
				if (c == ' ') {
					// blank gets extra work,
					// this solves the problem you get if you replace all
					// blanks with &nbsp;, if you do that you loss 
					// word breaking
					if (lastWasBlankChar) {
						lastWasBlankChar = false;
						sb.append("&nbsp;");
					}
					else {
						lastWasBlankChar = true;
						sb.append(' ');
					}
				}
				else {
					lastWasBlankChar = false;
					//
					// HTML Special Chars
					if (c == '"')
						sb.append("&quot;");
					else if (c == '&')
						sb.append("&amp;");
					else if (c == '<')
						sb.append("&lt;");
					else if (c == '>')
						sb.append("&gt;");
					else if (c == '%') // Android browser doesn't like % (iiizio)
						sb.append("&#37;");
					else if (c == '\n')
						// Handle Newline
						sb.append("\n<br/>");
					else {
						int ci = 0xffff & c;
						if (ci < 160 ) {
							// nothing special only 7 Bit
							sb.append(c);
						} else {
							// Not 7 Bit use the unicode system
							sb.append("&#");
							sb.append(Integer.valueOf(ci).toString());
							sb.append(';');
						}
					}
				}
			}
			return sb.toString();
		}
	}
}
