/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zheng.gallery3d.app;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Application;
import android.content.Context;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.zheng.gallery3d.R;
import com.zheng.gallery3d.data.DataManager;
import com.zheng.gallery3d.data.DownloadCache;
import com.zheng.gallery3d.data.ImageCacheService;
import com.zheng.gallery3d.gadget.WidgetUtils;
import com.zheng.gallery3d.picasasource.PicasaSource;
import com.zheng.gallery3d.util.GalleryUtils;
import com.zheng.gallery3d.util.LightCycleHelper;
import com.zheng.gallery3d.util.ThreadPool;

public class GalleryAppImpl extends Application implements GalleryApp {

	private static final String DOWNLOAD_FOLDER = "download";
	private static final long DOWNLOAD_CAPACITY = 64 * 1024 * 1024; // 64M

	private ImageCacheService mImageCacheService;
	private Object mLock = new Object();
	private DataManager mDataManager;
	private ThreadPool mThreadPool;
	private DownloadCache mDownloadCache;
	private StitchingProgressManager mStitchingProgressManager;
    private static float sPixelDensity = 1;
    private static ImageFileNamer sImageFileNamer;

	@Override
	public void onCreate() {
		super.onCreate();
		initialize(this);
		initializeAsyncTask();
		GalleryUtils.initialize(this);
		WidgetUtils.initialize(this);
		PicasaSource.initialize(this);

		mStitchingProgressManager = LightCycleHelper.createStitchingManagerInstance(this);
		if (mStitchingProgressManager != null) {
			mStitchingProgressManager.addChangeListener(getDataManager());
		}
	}

	public static void initialize(Context context) {
		DisplayMetrics metrics = new DisplayMetrics();
		WindowManager wm = (WindowManager)
				context.getSystemService(Context.WINDOW_SERVICE);
		wm.getDefaultDisplay().getMetrics(metrics);
		sPixelDensity = metrics.density;
		sImageFileNamer = new ImageFileNamer(
				context.getString(R.string.image_file_name_format));
	}

	private static class ImageFileNamer {
		private SimpleDateFormat mFormat;

		// The date (in milliseconds) used to generate the last name.
		private long mLastDate;

		// Number of names generated for the same second.
		private int mSameSecondCount;

		public ImageFileNamer(String format) {
			mFormat = new SimpleDateFormat(format);
		}

		public String generateName(long dateTaken) {
			Date date = new Date(dateTaken);
			String result = mFormat.format(date);

			// If the last name was generated for the same second,
			// we append _1, _2, etc to the name.
			if (dateTaken / 1000 == mLastDate / 1000) {
				mSameSecondCount++;
				result += "_" + mSameSecondCount;
			} else {
				mLastDate = dateTaken;
				mSameSecondCount = 0;
			}

			return result;
		}
	}

	@Override
	public Context getAndroidContext() {
		return this;
	}

	@Override
	public synchronized DataManager getDataManager() {
		if (mDataManager == null) {
			mDataManager = new DataManager(this);
			mDataManager.initializeSourceMap();
		}
		return mDataManager;
	}

	@Override
	public StitchingProgressManager getStitchingProgressManager() {
		return mStitchingProgressManager;
	}

	@Override
	public ImageCacheService getImageCacheService() {
		// This method may block on file I/O so a dedicated lock is needed here.
		synchronized (mLock) {
			if (mImageCacheService == null) {
				mImageCacheService = new ImageCacheService(getAndroidContext());
			}
			return mImageCacheService;
		}
	}

	@Override
	public synchronized ThreadPool getThreadPool() {
		if (mThreadPool == null) {
			mThreadPool = new ThreadPool();
		}
		return mThreadPool;
	}

	@Override
	public synchronized DownloadCache getDownloadCache() {
		if (mDownloadCache == null) {
			File cacheDir = new File(getExternalCacheDir(), DOWNLOAD_FOLDER);

			if (!cacheDir.isDirectory()) cacheDir.mkdirs();

			if (!cacheDir.isDirectory()) {
				throw new RuntimeException(
						"fail to create: " + cacheDir.getAbsolutePath());
			}
			mDownloadCache = new DownloadCache(this, cacheDir, DOWNLOAD_CAPACITY);
		}
		return mDownloadCache;
	}

	private void initializeAsyncTask() {
		// AsyncTask class needs to be loaded in UI thread.
		// So we load it here to comply the rule.
		try {
			Class.forName(AsyncTask.class.getName());
		} catch (ClassNotFoundException e) {
		}
	}
}
