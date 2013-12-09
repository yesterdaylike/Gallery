package com.zheng.gallery3d.app;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.youmi.android.AdManager;
import net.youmi.android.banner.AdSize;
import net.youmi.android.banner.AdView;
import net.youmi.android.spot.SpotDialogListener;
import net.youmi.android.spot.SpotManager;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;

import com.zheng.gallery3d.R;
import com.zheng.gallery3d.common.Utils;
import com.zheng.gallery3d.data.DataManager;
import com.zheng.gallery3d.data.MediaItem;
import com.zheng.gallery3d.data.MediaSet;
import com.zheng.gallery3d.data.Path;
import com.zheng.gallery3d.picasasource.PicasaSource;
import com.zheng.gallery3d.util.GalleryUtils;

public final class Gallery extends AbstractGalleryActivity implements OnCancelListener {
	public static final String EXTRA_SLIDESHOW = "slideshow";
	public static final String EXTRA_DREAM = "dream";
	public static final String EXTRA_CROP = "crop";

	public static final String ACTION_REVIEW = "com.zheng.camera.action.REVIEW";
	public static final String KEY_GET_CONTENT = "get-content";
	public static final String KEY_GET_ALBUM = "get-album";
	public static final String KEY_TYPE_BITS = "type-bits";
	public static final String KEY_MEDIA_TYPES = "mediaTypes";
	public static final String KEY_DISMISS_KEYGUARD = "dismiss-keyguard";

	private static final String TAG = "Gallery";
	private Dialog mVersionCheckDialog;
	public static final String CURRENT_VERSION = "current_version";
	private String galleryDirectory = Environment.getExternalStorageDirectory().toString()+"/Aphoto/";
	private String exDirectory = Environment.getExternalStorageDirectory().toString()+"/";
	// declare the dialog as a member field of your activity
	//private ProgressDialog mProgressDialog;
	private String localVersion;
	private String uri_version = "https://dl.dropboxusercontent.com/s/pmdlhgpn3g9tda4/version.txt?dl=1&token_hash=AAF7OIIyirM7C43bN875YWWwDM7SGZZ3VTuP3oPgYuls2w";
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_ACTION_BAR);
		requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);

		if (getIntent().getBooleanExtra(KEY_DISMISS_KEYGUARD, false)) {
			getWindow().addFlags(
					WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
		}

		setContentView(R.layout.main);
		
		AdManager.getInstance(this).init("6d5dfe14b9692302","0e7985b5e58e4f31", false);

	    /*SpotManager.getInstance(this).loadSpotAds();
	    SpotManager.getInstance(this).setSpotTimeout(50000);//5秒
	    Log.e("zhengwenhui", "onShowSuccess------------------");
	    SpotManager.getInstance(this).showSpotAds(this, new SpotDialogListener() {
            @Override
            public void onShowSuccess() {
                Log.e("Youmi", "onShowSuccess------------------");
            }

            @Override
            public void onShowFailed() {
                Log.e("Youmi", "onShowFailed------------------");
            }

        });*/
	    Log.e("zhengwenhui", "onShowFailed------------------");
		updatePhotos();//更新图片

		if (savedInstanceState != null) {
			getStateManager().restoreFromState(savedInstanceState);
		} else {
			initializeByIntent();
		}
		
		//实例化广告条
	    AdView adView = new AdView(this, AdSize.FIT_SCREEN);
	    //获取要嵌入广告条的布局
	    LinearLayout adLayout=(LinearLayout)findViewById(R.id.adLayout);
	    //将广告条加入到布局中
	    adLayout.addView(adView);
	}

	private void updatePhotos(){
		localVersion = getVersion();	//获得当前的版本号
		if( localVersion.equals("Aversion1.0") ){
			//如果是首版，先从Assets中复制一些图片到SD卡
			folderCopyFromAssetsToSD(this,"Aphoto");
			//更新图片的数据库，使图库可以检测到刚复制到SD卡的图片
			folderScan(galleryDirectory);
		}
		//从网络上下载其他新的图片
		downloadImage();
	}
	/**获得当前的版本号*/
	private String getVersion(){
		SharedPreferences settings = getSharedPreferences(CURRENT_VERSION, 0);
		return settings.getString(CURRENT_VERSION, "Aversion1.0");
	}

	/**更新版本号*/
	private boolean updateVersion(String updateVersion){
		SharedPreferences settings = getSharedPreferences(CURRENT_VERSION, 0);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString(CURRENT_VERSION, updateVersion);
		localVersion = updateVersion;
		return editor.commit();
	}

	public void fileScan(String file){
		Uri data = Uri.parse("file://"+file);
		sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, data));
	}

	public void folderScan(String path){
		File file = new File( path );
		if(file.isDirectory()){
			for (File f : file.listFiles()) {
				if(f.isFile()){
					//FILE TYPE
					fileScan(f.getAbsolutePath());
				}
				else {
					//FOLDER TYPE
					folderScan(f.getAbsolutePath());
				}
			}
		}
	}

	public void folderCopyFromAssetsToSD(Context ctxDealFile, String path) {
		try {
			String str[] = ctxDealFile.getAssets().list(path);
			if (str.length > 0) {//如果是目录
				File file = new File(exDirectory+path);
				file.mkdirs();
				for (String string : str) {
					path = path + "/" + string;
					folderCopyFromAssetsToSD(ctxDealFile, path);
					path = path.substring(0, path.lastIndexOf('/'));
				}
			} else {//如果是文件
				InputStream is = ctxDealFile.getAssets().open(path);
				FileOutputStream fos = new FileOutputStream(new File(exDirectory
						+ path));
				byte[] buffer = new byte[1024];
				while (true) {
					int len = is.read(buffer);
					if (len == -1) {
						break;
					}
					fos.write(buffer, 0, len);
				}
				is.close();
				fos.close();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void downloadImage(){
		// instantiate it within the onCreate method
		/*mProgressDialog = new ProgressDialog(this);
		mProgressDialog.setMessage("update progress");
		mProgressDialog.setIndeterminate(true);
		mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		mProgressDialog.setCancelable(true);*/

		// execute this when the downloader must be fired
		final DownloadTask downloadTask = new DownloadTask(this);
		//downloadTask.execute("https://dl.dropboxusercontent.com/s/co7rf23ryvay2hq/qqjietu.zip?dl=1&token_hash=AAEQLW8vRIcWeI6efV9TLz-1H-jGhQkIJ7sRPLbG4AxkkQ");
		downloadTask.execute(uri_version);

		/*mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				downloadTask.cancel(true);
			}
		});*/
	}

	// usually, subclasses of AsyncTask are declared inside the activity class.
	// that way, you can easily modify the UI thread from here
	private class DownloadTask extends AsyncTask<String, Integer, String> {
		private Context context;
		private String updateVersion;
		private String spec;

		public DownloadTask(Context context) {
			this.context = context;
		}

		@SuppressWarnings({ "resource", "resource", "resource" })
		@Override
		protected String doInBackground(String... sUrl) {
			// take CPU lock to prevent CPU from going off if the user 
			// presses the power button during download
			PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
			PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
					getClass().getName());
			wl.acquire();

			try {
				InputStream input = null;
				OutputStream output = null;
				HttpURLConnection connection = null;
				try {

					URL url = new URL(sUrl[0]);
					connection = (HttpURLConnection) url.openConnection();
					connection.connect();

					// expect HTTP 200 OK, so we don't mistakenly save error report 
					// instead of the file
					if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
						return "Server returned HTTP " + connection.getResponseCode() 
								+ " " + connection.getResponseMessage();
					// this will be useful to display download percentage
					// might be -1: server did not report the length
					int fileLength = connection.getContentLength();
					// download the file
					input = connection.getInputStream();
					String[] versionUris = getStringArray(input);
					if (input != null){input.close();}

					for (int i=0; i<versionUris.length; i++) {
						updateVersion = versionUris[i++];
						Log.i(TAG,"updateVersion: "+updateVersion);
						if(updateVersion.compareTo(localVersion)>0){
							spec = versionUris[i];
							Log.i(TAG,"spec: "+spec);
						}
						else{
							continue;
						}

						url = new URL(spec);
						connection = (HttpURLConnection) url.openConnection();
						connection.connect();

						// expect HTTP 200 OK, so we don't mistakenly save error report 
						// instead of the file
						if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
							return "Server returned HTTP " + connection.getResponseCode() 
									+ " " + connection.getResponseMessage();
						Log.i(TAG,"fileLength:"+fileLength);
						// this will be useful to display download percentage
						// might be -1: server did not report the length
						fileLength = connection.getContentLength();

						input = connection.getInputStream();
						Log.i(TAG,"before output:"+galleryDirectory+"  "+updateVersion+".zip");
						isExist(galleryDirectory);
						output = new FileOutputStream(galleryDirectory+updateVersion+".zip");
						Log.i(TAG,"output:"+galleryDirectory+updateVersion+".zip");
						byte data[] = new byte[4096];
						long total = 0;
						int count;
						while ((count = input.read(data)) != -1) {
							// allow canceling with back button
							if (isCancelled())
								return null;
							total += count;
							// publishing the progress....
							if (fileLength > 0) // only if total length is known
								publishProgress((int) (total * 100 / fileLength));
							output.write(data, 0, count);
						}
						Log.i(TAG,"updateVersion before");
						updateVersion(updateVersion);
						Log.i(TAG,"unpackZip before");
						unpackZip(galleryDirectory, updateVersion+".zip");
						Log.i(TAG,"folderScan before");
						folderScan(galleryDirectory + updateVersion);
					}
				} catch (Exception e) {
					Log.i(TAG,"Exception");
					return e.toString();
				} finally {
					try {
						if (output != null)
							output.close();
						if (input != null)
							input.close();
					} 
					catch (IOException ignored) { }

					if (connection != null)
						connection.disconnect();
				}
			} finally {
				wl.release();
			}
			return null;
		}

		public void isExist(String path){
			File file = new File(path);
			//判断文件夹是否存在,如果不存在则创建文件夹
			if (!file.exists()) {
				file.mkdir();
			}
		}


		public String[] getStringArray(InputStream inputStream) {
			InputStreamReader inputStreamReader = null;  
			try {  
				inputStreamReader = new InputStreamReader(inputStream, "gbk");  
			} catch (UnsupportedEncodingException e1) {  
				e1.printStackTrace();  
			}  
			BufferedReader reader = new BufferedReader(inputStreamReader);  
			StringBuffer sb = new StringBuffer("");
			String line;  
			try {  
				while ((line = reader.readLine()) != null) {
					sb.append(line);
					Log.i(TAG,"line: "+line);
					sb.append("\n");
				}  
			} catch (IOException e) {
				e.printStackTrace();
			}  

			return sb.toString().split("\n");
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			//mProgressDialog.show();
		}

		@Override
		protected void onProgressUpdate(Integer... progress) {
			super.onProgressUpdate(progress);
			// if we get here, length is known, now set indeterminate to false
			/*mProgressDialog.setIndeterminate(false);
			mProgressDialog.setMax(100);
			mProgressDialog.setProgress(progress[0]);*/
		}

		@Override
		protected void onPostExecute(String result) {
			//mProgressDialog.dismiss();
			if (result != null)
				Log.e("result != null", "Download error: "+result);
			else
				Log.e("result != null", "Photo downloaded");
		}
	}

	private boolean unpackZip(String path, String zipname)
	{       
		InputStream is;
		ZipInputStream zis;
		try 
		{
			String filename;
			is = new FileInputStream(path + zipname);
			zis = new ZipInputStream(new BufferedInputStream(is));          
			ZipEntry ze;
			byte[] buffer = new byte[1024];
			int count;

			while ((ze = zis.getNextEntry()) != null) 
			{
				// zapis do souboru
				filename = ze.getName();

				// Need to create directories if not exists, or
				// it will generate an Exception...
				if (ze.isDirectory()) {
					File fmd = new File(path + filename);
					fmd.mkdirs();
					continue;
				}

				FileOutputStream fout = new FileOutputStream(path + filename);

				// cteni zipu a zapis
				while ((count = zis.read(buffer)) != -1) 
				{
					fout.write(buffer, 0, count);             
				}

				fout.close();               
				zis.closeEntry();
			}

			zis.close();
		} 
		catch(IOException e)
		{
			e.printStackTrace();
			return false;
		}

		return true;
	}

	private void initializeByIntent() {
		Intent intent = getIntent();
		String action = intent.getAction();

		if (Intent.ACTION_GET_CONTENT.equalsIgnoreCase(action)) {
			startGetContent(intent);
		} else if (Intent.ACTION_PICK.equalsIgnoreCase(action)) {
			// We do NOT really support the PICK intent. Handle it as
			// the GET_CONTENT. However, we need to translate the type
			// in the intent here.
			Log.w(TAG, "action PICK is not supported");
			String type = Utils.ensureNotNull(intent.getType());
			if (type.startsWith("vnd.android.cursor.dir/")) {
				if (type.endsWith("/image")) intent.setType("image/*");
				if (type.endsWith("/video")) intent.setType("video/*");
			}
			startGetContent(intent);
		} else if (Intent.ACTION_VIEW.equalsIgnoreCase(action)
				|| ACTION_REVIEW.equalsIgnoreCase(action)){
			startViewAction(intent);
		} else {
			startDefaultPage();
		}
	}

	public void startDefaultPage() {
		PicasaSource.showSignInReminder(this);
		Bundle data = new Bundle();
		data.putString(AlbumSetPage.KEY_MEDIA_PATH,
				getDataManager().getTopSetPath(DataManager.INCLUDE_ALL));
		getStateManager().startState(AlbumSetPage.class, data);
		mVersionCheckDialog = PicasaSource.getVersionCheckDialog(this);
		if (mVersionCheckDialog != null) {
			mVersionCheckDialog.setOnCancelListener(this);
		}
	}

	private void startGetContent(Intent intent) {
		Bundle data = intent.getExtras() != null
				? new Bundle(intent.getExtras())
		: new Bundle();
				data.putBoolean(KEY_GET_CONTENT, true);
				int typeBits = GalleryUtils.determineTypeBits(this, intent);
				data.putInt(KEY_TYPE_BITS, typeBits);
				data.putString(AlbumSetPage.KEY_MEDIA_PATH,
						getDataManager().getTopSetPath(typeBits));
				getStateManager().startState(AlbumSetPage.class, data);
	}

	private String getContentType(Intent intent) {
		String type = intent.getType();
		if (type != null) {
			return GalleryUtils.MIME_TYPE_PANORAMA360.equals(type)
					? MediaItem.MIME_TYPE_JPEG : type;
		}

		Uri uri = intent.getData();
		try {
			return getContentResolver().getType(uri);
		} catch (Throwable t) {
			Log.w(TAG, "get type fail", t);
			return null;
		}
	}

	private void startViewAction(Intent intent) {
		Boolean slideshow = intent.getBooleanExtra(EXTRA_SLIDESHOW, false);
		if (slideshow) {
			getActionBar().hide();
			DataManager manager = getDataManager();
			Path path = manager.findPathByUri(intent.getData(), intent.getType());
			if (path == null || manager.getMediaObject(path)
					instanceof MediaItem) {
				path = Path.fromString(
						manager.getTopSetPath(DataManager.INCLUDE_IMAGE));
			}
			Bundle data = new Bundle();
			data.putString(SlideshowPage.KEY_SET_PATH, path.toString());
			data.putBoolean(SlideshowPage.KEY_RANDOM_ORDER, true);
			data.putBoolean(SlideshowPage.KEY_REPEAT, true);
			if (intent.getBooleanExtra(EXTRA_DREAM, false)) {
				data.putBoolean(SlideshowPage.KEY_DREAM, true);
			}
			getStateManager().startState(SlideshowPage.class, data);
		} else {
			Bundle data = new Bundle();
			DataManager dm = getDataManager();
			Uri uri = intent.getData();
			String contentType = getContentType(intent);
			if (contentType == null) {
				Log.e("result != null", getString(R.string.no_such_item));
				finish();
				return;
			}
			if (uri == null) {
				int typeBits = GalleryUtils.determineTypeBits(this, intent);
				data.putInt(KEY_TYPE_BITS, typeBits);
				data.putString(AlbumSetPage.KEY_MEDIA_PATH,
						getDataManager().getTopSetPath(typeBits));
				getStateManager().startState(AlbumSetPage.class, data);
			} else if (contentType.startsWith(
					ContentResolver.CURSOR_DIR_BASE_TYPE)) {
				int mediaType = intent.getIntExtra(KEY_MEDIA_TYPES, 0);
				if (mediaType != 0) {
					uri = uri.buildUpon().appendQueryParameter(
							KEY_MEDIA_TYPES, String.valueOf(mediaType))
							.build();
				}
				Path setPath = dm.findPathByUri(uri, null);
				MediaSet mediaSet = null;
				if (setPath != null) {
					mediaSet = (MediaSet) dm.getMediaObject(setPath);
				}
				if (mediaSet != null) {
					if (mediaSet.isLeafAlbum()) {
						data.putString(AlbumPage.KEY_MEDIA_PATH, setPath.toString());
						data.putString(AlbumPage.KEY_PARENT_MEDIA_PATH,
								dm.getTopSetPath(DataManager.INCLUDE_ALL));
						getStateManager().startState(AlbumPage.class, data);
					} else {
						data.putString(AlbumSetPage.KEY_MEDIA_PATH, setPath.toString());
						getStateManager().startState(AlbumSetPage.class, data);
					}
				} else {
					startDefaultPage();
				}
			} else {
				Path itemPath = dm.findPathByUri(uri, contentType);
				Path albumPath = dm.getDefaultSetOf(itemPath);

				data.putString(PhotoPage.KEY_MEDIA_ITEM_PATH, itemPath.toString());

				// TODO: Make the parameter "SingleItemOnly" public so other
				//       activities can reference it.
				boolean singleItemOnly = (albumPath == null)
						|| intent.getBooleanExtra("SingleItemOnly", false);
				if (!singleItemOnly) {
					data.putString(PhotoPage.KEY_MEDIA_SET_PATH, albumPath.toString());
					// when FLAG_ACTIVITY_NEW_TASK is set, (e.g. when intent is fired
					// from notification), back button should behave the same as up button
					// rather than taking users back to the home screen
					if (intent.getBooleanExtra(PhotoPage.KEY_TREAT_BACK_AS_UP, false)
							|| ((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0)) {
						data.putBoolean(PhotoPage.KEY_TREAT_BACK_AS_UP, true);
					}
				}

				getStateManager().startState(PhotoPage.class, data);
			}
		}
	}

	@Override
	protected void onResume() {
		Utils.assertTrue(getStateManager().getStateCount() > 0);
		super.onResume();
		if (mVersionCheckDialog != null) {
			mVersionCheckDialog.show();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mVersionCheckDialog != null) {
			mVersionCheckDialog.dismiss();
		}
	}

	@Override
	public void onCancel(DialogInterface dialog) {
		if (dialog == mVersionCheckDialog) {
			mVersionCheckDialog = null;
		}
	}
}
