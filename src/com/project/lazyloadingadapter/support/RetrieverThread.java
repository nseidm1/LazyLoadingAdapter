package com.project.lazyloadingadapter.support;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ThumbnailUtils;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.widget.Toast;
import com.project.lazyloadingadapter.R;
import com.project.lazyloadingadapter.helpers.Exif;
import com.project.lazyloadingadapter.helpers.Log;
import com.project.lazyloadingadapter.objects.CustomArrayBlockingQueue;
import com.project.lazyloadingadapter.objects.CustomLRUCache;
import com.project.lazyloadingadapter.objects.LoadingCompleteCallback;
import com.project.lazyloadingadapter.objects.QueueObject;

public class RetrieverThread<E> extends Thread {
    private static final String TAG = RetrieverThread.class.getSimpleName();
    private Context mContext;
    private int mWidth;
    private int mHeight;
    private boolean mIsImages;
    private boolean mAlive = true;
    private CustomLRUCache<E> mCache;
    private BitmapFactory.Options mOptions;
    private Handler mUiThreadHandler;
    private WaitingNetwork mWaitingNetwork;
    private ConnectivityManager mConnectivityManager;
    private NetworkInfo mMobileInfo;
    private NetworkInfo mWifiInfo;
    private NetworkInfo mWiMaxInfo;
    private LoadingCompleteCallback<E> mLoadingCompleteCallback;
    private CustomArrayBlockingQueue<E> mArrayBlockingQueue = new CustomArrayBlockingQueue<E>(200, true);

    private class WaitingNetwork implements Runnable {
	@Override
	public void run() {
	    if (mContext != null)
		Toast.makeText(mContext, mContext.getResources().getString(R.string.waiting_network), Toast.LENGTH_SHORT).show();
	}
    }

    public RetrieverThread(Context context, Handler uiThreadHandler, LoadingCompleteCallback<E> loadingCompleteCallback, CustomLRUCache<E> cache, int width,
	    int height, boolean isImages) {
	mContext = context;
	mCache = cache;
	mWidth = width;
	mHeight = height;
	mIsImages = isImages;
	Log.d(TAG, "Is Images: " + mIsImages);
	mUiThreadHandler = uiThreadHandler;
	mWaitingNetwork = new WaitingNetwork();
	mLoadingCompleteCallback = loadingCompleteCallback;
	mOptions = new BitmapFactory.Options();
	mOptions.inPurgeable = true;
	mOptions.inInputShareable = true;
	mOptions.inDither = true;
	mConnectivityManager = ((ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE));
    }

    @Override
    public void run() {
	Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
	while (mAlive) {
	    try {
		QueueObject<E> object = mArrayBlockingQueue.take();
		// The following while loop is used to accommodate unexpected
		// out of memory errors decoding the image file
		// Pre-scaling is used, but wtf situations occur. 10 is overkill
		// but...
		boolean success = false;
		int counter = 0;
		while (success == false && counter < 10 && object != null && object.getPathIDOrUri() != null) {
		    Bitmap thumbnail = null;
		    try {
			// If the cache does not contain the object retrieve the
			// object and cache it
			if (mCache.get(object.getPathIDOrUri()) == null) {
			    if (object.getPathIDOrUri() instanceof Uri) {
				String path = object.getPathIDOrUri().toString();
				File file = new File(mContext.getCacheDir(), path.substring(path.lastIndexOf("/") + 1));
				if (!file.exists())
				    processRemoteFileToLocalFile(object, file);
				if (mIsImages)
				    thumbnail = processImagePath(file.getPath(), object.getPathIDOrUri());
				else
				    thumbnail = processVideoPath(file.getPath(), object.getPathIDOrUri());
			    } else if (object.getPathIDOrUri() instanceof Long) {
				if (mIsImages)
				    thumbnail = processImageThumb((Long) object.getPathIDOrUri(), object.getPathIDOrUri());
				else
				    thumbnail = processVideoThumb((Long) object.getPathIDOrUri(), object.getPathIDOrUri());
			    } else {
				if (mIsImages)
				    thumbnail = processImagePath((String) object.getPathIDOrUri(), object.getPathIDOrUri());
				else
				    thumbnail = processVideoPath((String) object.getPathIDOrUri(), object.getPathIDOrUri());
			    }
			}
			// If the cache contains the object just update it in
			// the UI
			else {
			    thumbnail = mCache.get(object.getPathIDOrUri());
			}
			mLoadingCompleteCallback.updateImageInUI(object, thumbnail);
			success = true;
		    } catch (OutOfMemoryError e) {
			// The sample size will continuously increase up until
			// 10. This methodology accommodates out of memory
			// issues
			// associated with image loading. 10 is overkill.
			// Success = true is not set here to recycle through the
			// while loop.
			System.gc();
			mOptions.inSampleSize++;
			counter++;
			Log.e(TAG, "Out of memory decoding image");
		    } catch (Throwable e) {
			// Cache a missing image placeholder, and update the UI
			thumbnail = cacheMissingImagePlaceholder(object);
			mLoadingCompleteCallback.updateImageInUI(object, thumbnail);
			e.printStackTrace();
			Log.e(TAG, "Uri syntax exception decoding image");
			success = true;
		    }
		}
	    } catch (InterruptedException e) {
		// Array blocking queue interrupted
		Log.e(TAG, "Image loader thread interrupted while decoding an image, thread may still be still alive");
	    }
	}
    }

    private Bitmap cacheMissingImagePlaceholder(QueueObject<E> object) {
	Bitmap temp = null;
	boolean success = false;
	int counter = 0;
	while (success == false && counter < 10) {
	    try {
		temp = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeResource(mContext.getResources(), R.drawable.missing_file, mOptions), mWidth,
			mHeight, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
		mCache.put(object.getPathIDOrUri(), temp);
		success = true;
	    } catch (OutOfMemoryError e) {
		System.gc();
		counter++;
	    }
	}
	return temp;
    }

    private void processRemoteFileToLocalFile(QueueObject<E> object, File file) throws IOException, URISyntaxException, InterruptedException,
	    NullPointerException {
	while (!checkNetworkState(mContext)) {
	    Thread.sleep(3000);
	    mUiThreadHandler.post(mWaitingNetwork);
	}
	HttpEntity entity = setupHttpEntity(object);
	BufferedHttpEntity bufHttpEntity = new BufferedHttpEntity(entity);
	InputStream instream = bufHttpEntity.getContent();
	BufferedInputStream bIn = new BufferedInputStream(instream, 2048);
	// The output stream is to a new file in the apps cache directory using
	// the file name
	FileOutputStream fOut = new FileOutputStream(file.getPath());
	BufferedOutputStream bOut = new BufferedOutputStream(fOut, 2048);
	int n = 0;
	byte[] buffer = new byte[2048];
	while ((n = bIn.read(buffer, 0, 2048)) != -1) {
	    bOut.write(buffer, 0, n);
	}
	// Close all streams to release resources
	bOut.flush();
	bOut.close();
	fOut.flush();
	fOut.close();
	bIn.close();
	instream.close();
	bufHttpEntity.consumeContent();
    }

    private HttpEntity setupHttpEntity(QueueObject<E> object) throws URISyntaxException, ClientProtocolException, IOException {
	HttpGet httpGet = new HttpGet();
	httpGet.setURI(new URI(object.getPathIDOrUri().toString()));
	HttpClient httpclient = new DefaultHttpClient();
	HttpResponse response = (HttpResponse) httpclient.execute(httpGet);
	HttpEntity entity = response.getEntity();
	return entity;
    }

    private Bitmap processImageThumb(Long id, E pathIDOrUri) throws NullPointerException, OutOfMemoryError, IOException {
	// No need to prescale, it's gonna be tiny already
	Bitmap temp = ThumbnailUtils.extractThumbnail(MediaStore.Images.Thumbnails.getThumbnail(mContext.getContentResolver(), id,
		MediaStore.Images.Thumbnails.MICRO_KIND, null), mWidth, mHeight, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
	// If the thumb is null try and get it directly from the full sized
	// media
	if (temp == null) {
	    Cursor cursor = mContext.getContentResolver().query(Uri.withAppendedPath(Images.Media.EXTERNAL_CONTENT_URI, Long.toString(id)),
		    new String[] { Images.Media.DATA }, null, null, null);
	    if (cursor != null && cursor.moveToFirst()) {
		temp = processImagePath(cursor.getString(cursor.getColumnIndex(Images.Media.DATA)), pathIDOrUri);
	    }
	    closeCursor(cursor);
	}
	if (temp == null) {
	    Cursor cursor = mContext.getContentResolver().query(Uri.withAppendedPath(Images.Media.INTERNAL_CONTENT_URI, Long.toString(id)),
		    new String[] { Images.Media.DATA }, null, null, null);
	    if (cursor != null && cursor.moveToFirst()) {
		temp = processImagePath(cursor.getString(cursor.getColumnIndex(Images.Media.DATA)), pathIDOrUri);
	    }
	    closeCursor(cursor);
	}
	mCache.put(pathIDOrUri, temp);
	return temp;
    }

    private Bitmap processVideoThumb(Long id, E pathIDOrUri) throws NullPointerException, OutOfMemoryError {
	// No need to prescale, it's gonna be tiny already
	Bitmap temp = ThumbnailUtils.extractThumbnail(MediaStore.Video.Thumbnails.getThumbnail(mContext.getContentResolver(), id,
		MediaStore.Images.Thumbnails.MICRO_KIND, null), mWidth, mHeight, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
	// If the thumb is null try and get it directly from the full sized
	// media
	if (temp == null) {
	    Cursor cursor = mContext.getContentResolver().query(Uri.withAppendedPath(Video.Media.EXTERNAL_CONTENT_URI, Long.toString(id)),
		    new String[] { Video.Media.DATA }, null, null, null);
	    if (cursor != null && cursor.moveToFirst()) {
		temp = processVideoPath(cursor.getString(cursor.getColumnIndex(Video.Media.DATA)), pathIDOrUri);
	    }
	    closeCursor(cursor);
	}
	if (temp == null) {
	    Cursor cursor = mContext.getContentResolver().query(Uri.withAppendedPath(Video.Media.INTERNAL_CONTENT_URI, Long.toString(id)),
		    new String[] { Video.Media.DATA }, null, null, null);
	    if (cursor != null && cursor.moveToFirst()) {
		temp = processVideoPath(cursor.getString(cursor.getColumnIndex(Video.Media.DATA)), pathIDOrUri);
	    }
	    closeCursor(cursor);
	}
	mCache.put(pathIDOrUri, temp);
	return temp;
    }

    private Bitmap processVideoPath(String path, E pathIDOrUri) throws NullPointerException, OutOfMemoryError {
	Bitmap temp = ThumbnailUtils.extractThumbnail(ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND), mWidth, mHeight,
		ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
	mCache.put(pathIDOrUri, temp);
	return temp;
    }

    private Bitmap processImagePath(String path, E pathIDOrUri) throws NullPointerException, OutOfMemoryError, IOException {
	// Prescale image for known resolutions that will cause out of memory
	// situations
	preScaleImage(path);
	byte[] tempBytes = getBitmapBytes(path);
	Bitmap tempBitmap = BitmapFactory.decodeByteArray(tempBytes, 0, tempBytes.length, mOptions);
	int currentRotation = Exif.getOrientation(tempBytes);
	if (currentRotation == 0 || (currentRotation % 360) == 0) {
	    return tempBitmap;
	}
	// Rotate based on EXIF data
	Matrix matrix = new Matrix();
	matrix.setRotate(currentRotation, tempBitmap.getWidth() / 2, tempBitmap.getHeight() / 2);
	Bitmap temp = ThumbnailUtils.extractThumbnail(Bitmap.createBitmap(tempBitmap, 0, 0, tempBitmap.getWidth(), tempBitmap.getHeight(), matrix, true),
		mWidth, mHeight, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
	mCache.put(pathIDOrUri, temp);
	return temp;
    }

    private byte[] getBitmapBytes(String path) throws IOException {
	File file = new File(path);
	InputStream is = new FileInputStream(file);
	ByteArrayOutputStream bos = new ByteArrayOutputStream();
	byte[] b = new byte[1024];
	int bytesRead;
	while ((bytesRead = is.read(b)) != -1) {
	    bos.write(b, 0, bytesRead);
	}
	return bos.toByteArray();
    }

    public synchronized void stopThread() {
	// Set the while loop boolean to false, and add a KILL object to the
	// queue to unblock it if necessary
	mAlive = false;
	if (mArrayBlockingQueue.isEmpty())
	    mArrayBlockingQueue.offer(new QueueObject<E>(1, null, null, null));
    }

    /**
     * @param QueueObject
     * @throws InterruptedException
     */
    public void loadImage(final QueueObject<E> object) {
	mArrayBlockingQueue.remove(object);
	try {
	    mArrayBlockingQueue.offer(object, 2000, TimeUnit.MILLISECONDS);
	} catch (InterruptedException e) {
	    mUiThreadHandler.post(new Runnable() {
		@Override
		public void run() {
		    if (mContext != null)
			Toast.makeText(mContext, mContext.getResources().getString(R.string.overload), Toast.LENGTH_SHORT).show();
		}
	    });
	    e.printStackTrace();
	}
    }

    private void preScaleImage(String path) {
	// Always start with a sample size of 1
	mOptions.inSampleSize = 1;
	mOptions.inJustDecodeBounds = true;
	BitmapFactory.decodeFile(path, mOptions);
	if (mOptions.outWidth >= 960 && mOptions.outWidth < 1600) {
	    mOptions.inSampleSize = 2;
	} else if (mOptions.outWidth >= 1600 && mOptions.outWidth < 1920) {
	    mOptions.inSampleSize = 3;
	} else if (mOptions.outWidth >= 1920) {
	    mOptions.inSampleSize = 4;
	}
	mOptions.inJustDecodeBounds = false;
    }

    private boolean checkNetworkState(Context context) {
	// Check every aspect of wifi and mobile connections
	mMobileInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
	mWifiInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
	mWiMaxInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIMAX);
	if ((mWiMaxInfo != null && mWiMaxInfo.isAvailable()) || (mWiMaxInfo != null && mWiMaxInfo.isConnected())
		|| (mWifiInfo != null && mWifiInfo.isAvailable()) || (mWifiInfo != null && mWifiInfo.isConnected())
		|| (mMobileInfo != null && mMobileInfo.isAvailable()) || (mMobileInfo != null && mMobileInfo.isConnected()))
	    return true;
	return false;
    }

    private void closeCursor(Cursor cursor) {
	if (cursor != null && !cursor.isClosed())
	    cursor.close();
    }
}