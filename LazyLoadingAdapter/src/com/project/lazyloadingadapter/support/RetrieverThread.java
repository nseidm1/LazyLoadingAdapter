package com.project.lazyloadingadapter.support;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ArrayBlockingQueue;
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
import android.media.ThumbnailUtils;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.support.v4.util.LruCache;
import android.widget.Toast;
import com.project.lazyloadingadapter.R;
import com.project.lazyloadingadapter.helpers.Log;
import com.project.lazyloadingadapter.objects.LoadingCompleteCallback;
import com.project.lazyloadingadapter.objects.QueueObject;
public class RetrieverThread<E> extends Thread
{
    private static final String TAG = RetrieverThread.class.getSimpleName();
    private Context mContext;
    private LoadingCompleteCallback<E> mLoadingCompleteCallback;
    private BitmapFactory.Options mOptions;
    private ArrayBlockingQueue<QueueObject<E>> mArrayBlockingQueue;
    private boolean mAlive = true;
    private LruCache<Object, Bitmap> mCache;
    private int mWidth;
    private int mHeight;
    private Handler mUiThreadHandler;
    private WaitingNetwork mWaitingNetwork;
    private ConnectivityManager mConnectivityManager;
    private NetworkInfo mMobileInfo;
    private NetworkInfo mWifiInfo;
    private NetworkInfo mWiMaxInfo;
    private boolean mIsImages;

    private class WaitingNetwork implements Runnable
    {
	@Override
	public void run()
	{
	    if (mContext != null)
		Toast.makeText(mContext, mContext.getResources().getString(R.string.waiting_network), Toast.LENGTH_SHORT).show();
	}
    }
    public RetrieverThread(Context context, Handler uiThreadHandler, LoadingCompleteCallback<E> loadingCompleteCallback, LruCache<Object, Bitmap> cache, int width, int height, boolean isImages)
    {
	mContext = context;
	mIsImages = isImages;
	mUiThreadHandler = uiThreadHandler;
	mWaitingNetwork = new WaitingNetwork();
	mLoadingCompleteCallback = loadingCompleteCallback;
	mOptions = new BitmapFactory.Options();
	mOptions.inPurgeable = true;
	mOptions.inInputShareable = true;
	mOptions.inDither = true;
	mArrayBlockingQueue = new ArrayBlockingQueue<QueueObject<E>>(200, true);
	mConnectivityManager = ((ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE));
	mCache = cache;
	mWidth = width;
	mHeight = height;
    }
    @Override
    public void run()
    {
	while (mAlive)
	{
	    try
	    {
		QueueObject<E> object = mArrayBlockingQueue.take();
		// The following while loop is used to accommodate unexpected
		// out of memory errors decoding the image file
		// Pre-scaling ins used, but wtf situations occur. 10 is
		// overkill.
		boolean success = false;
		int counter = 0;
		while (success == false && counter < 10 && object != null && object.getPathIDOrUri() != null)
		{
		    Bitmap temp;
		    try
		    {
			// If the cache does not contain the object retrieve the object
			if (mCache.get(object.getPathIDOrUri()) == null)
			{
			    // Remote content Uri
			    if (object.getPathIDOrUri() instanceof Uri)
			    {
				String path = object.getPathIDOrUri().toString();
				File file = new File(mContext.getCacheDir(), path.substring(path.lastIndexOf("/") + 1));
				if (!file.exists())
				{
				    processRemoteFileToLocalFile(object, file);
				}
				Log.d(TAG, "Local File Path of Remote File: " + file.getPath());
				temp = processImagePath(file.getPath(), object.getPathIDOrUri());
			    }
			    // If the QueueObject contains a Long it entails a
			    // List of image thumb IDs has been supplied
			    else if (object.getPathIDOrUri() instanceof Long)
			    {
				if (mIsImages)
				{
				    temp = processImageThumb((Long) object.getPathIDOrUri(), object.getPathIDOrUri());
				}
				else
				{
				    temp = processVideoThumb((Long) object.getPathIDOrUri(), object.getPathIDOrUri());
				}
			    }
			    else
			    {
				//If we get this far it's a String
				if (mIsImages)
				{
				    temp = processImagePath((String) object.getPathIDOrUri(), object.getPathIDOrUri());
				}
				// lets test if it's a video file
				else
				{
				    temp = processVideoPath((String) object.getPathIDOrUri(), object.getPathIDOrUri());
				}
			    }
			}
			else
			{
			    temp = mCache.get(object.getPathIDOrUri());
			}
			// If the cache contains the object just update it in
			// the UI
			mLoadingCompleteCallback.updateImageInUI(object, temp);
			success = true;
		    }
		    catch (OutOfMemoryError e)
		    {
			// The sample size will continuously increase up until
			// 10. This methodology accommodates out of memory issues
			// associated with image loading. 10 is overkill.
			// Success = true is not set here to recycle through the
			// while loop
			System.gc();
			Log.d(TAG, "out of memory decoding thumb by path");
			mOptions.inSampleSize++;
			counter++;
			Log.e(TAG, "out of memory decoding image");
		    }
		    catch (Exception e)
		    {
			// Cache a missing image placeholder, and update the UI
			temp = cacheMissingImagePlaceholder(object);
			mLoadingCompleteCallback.updateImageInUI(object, temp);
			Log.e(TAG, "uri syntax exception decoding image");
			success = true;
		    }
		}
	    }
	    catch (InterruptedException e)
	    {
		// Array blocking queue interrupted
		Log.e(TAG, "image loader thread interrupted while decoding an image, thread is still alive");
	    }
	}
    }
    private Bitmap cacheMissingImagePlaceholder(QueueObject<E> object)
    {
	Bitmap temp = null;
	boolean success = false;
	int counter = 0;
	while (success == false && counter < 10)
	{
	    try
	    {
		temp = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeResource(mContext.getResources(), R.drawable.missing_file, mOptions), mWidth, mHeight, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
		mCache.put(object.getPosition(), temp);
		success = true;
	    }
	    catch (OutOfMemoryError e)
	    {
		System.gc();
		counter++;
	    }
	}
	return temp;
    }
    private void processRemoteFileToLocalFile(QueueObject<E> object, File file) throws IOException, URISyntaxException, InterruptedException, NullPointerException
    {
	while (!checkNetworkState(mContext))
	{
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
	while ((n = bIn.read(buffer, 0, 2048)) != -1)
	{
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
    private HttpEntity setupHttpEntity(QueueObject<E> object) throws URISyntaxException, ClientProtocolException, IOException
    {
	HttpGet httpGet = new HttpGet();
	httpGet.setURI(new URI(object.getPathIDOrUri().toString()));
	HttpClient httpclient = new DefaultHttpClient();
	HttpResponse response = (HttpResponse) httpclient.execute(httpGet);
	HttpEntity entity = response.getEntity();
	return entity;
    }
    private Bitmap processVideoPath(String path, Object pathOrId) throws NullPointerException, OutOfMemoryError
    {
	Bitmap temp = ThumbnailUtils.extractThumbnail(ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND), mWidth, mHeight, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
	mCache.put(pathOrId, temp);
	return temp;
    }
    private Bitmap processImageThumb(Long id, Object pathOrId) throws NullPointerException, OutOfMemoryError
    {
	// No need to prescale, it's gonna be tiny already
	Bitmap temp = ThumbnailUtils.extractThumbnail(MediaStore.Images.Thumbnails.getThumbnail(mContext.getContentResolver(), id, MediaStore.Images.Thumbnails.MICRO_KIND, null), mWidth, mHeight, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
	//If the thumb is null try and get it directly from the full sized media
	if (temp == null)
	{
	    Cursor cursor = mContext.getContentResolver().query(Uri.withAppendedPath(Images.Media.EXTERNAL_CONTENT_URI, Long.toString(id)), new String[]
	    { Images.Media.DATA }, null, null, null);
	    if (cursor != null && cursor.moveToFirst())
	    {
		temp = processImagePath(cursor.getString(cursor.getColumnIndex(Images.Media.DATA)), pathOrId);
	    }
	    closeCursor(cursor);
	}
	if (temp == null)
	{
	    Cursor cursor = mContext.getContentResolver().query(Uri.withAppendedPath(Images.Media.INTERNAL_CONTENT_URI, Long.toString(id)), new String[]{Images.Media.DATA}, null, null, null);
	    if (cursor != null && cursor.moveToFirst())
	    {
		temp = processImagePath(cursor.getString(cursor.getColumnIndex(Images.Media.DATA)), pathOrId);
	    }
	    closeCursor(cursor);
	}
	mCache.put(pathOrId, temp);
	return temp;
    }
    private Bitmap processVideoThumb(Long id, Object pathOrId) throws NullPointerException, OutOfMemoryError
    {
	// No need to prescale, it's gonna be tiny already
	Bitmap temp = ThumbnailUtils.extractThumbnail(MediaStore.Video.Thumbnails.getThumbnail(mContext.getContentResolver(), id, MediaStore.Images.Thumbnails.MICRO_KIND, null), mWidth, mHeight, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
	//If the thumb is null try and get it directly from the full sized media
	if (temp == null)
	{
	    Cursor cursor = mContext.getContentResolver().query(Uri.withAppendedPath(Video.Media.EXTERNAL_CONTENT_URI, Long.toString(id)), new String[]
	    { Video.Media.DATA }, null, null, null);
	    if (cursor != null && cursor.moveToFirst())
	    {
		temp = processVideoPath(cursor.getString(cursor.getColumnIndex(Video.Media.DATA)), pathOrId);
	    }
		closeCursor(cursor);
	}
	if (temp == null)
	{
	    Cursor cursor = mContext.getContentResolver().query(Uri.withAppendedPath(Video.Media.INTERNAL_CONTENT_URI, Long.toString(id)), new String[]{Video.Media.DATA}, null, null, null);
	    if (cursor != null && cursor.moveToFirst())
	    {
		temp = processVideoPath(cursor.getString(cursor.getColumnIndex(Video.Media.DATA)), pathOrId);
	    }
	    closeCursor(cursor);
	}
	mCache.put(pathOrId, temp);
	return temp;
    }
    private Bitmap processImagePath(String path, Object pathOrId) throws NullPointerException, OutOfMemoryError
    {
	// Prescale image for known resolutions that will cause out of memory situations
	preScaleImage(path);
	Bitmap temp = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(path, mOptions), mWidth, mHeight, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
	mCache.put(pathOrId, temp);
	return temp;
    }
    public synchronized void stopThread()
    {
	// Set the while loop boolean to false, and add a KILL object to the queue to unblock it if necessary
	mAlive = false;
	if (mArrayBlockingQueue.isEmpty())
	{
	    mArrayBlockingQueue.offer(new QueueObject<E>(1, null, null, null));
	}
    }
    /**
     * @param QueueObject
     */
    public void loadImage(final QueueObject<E> object)
    {
	// Queue object has an override equals method, thus remove and offer
	// will ensure only AbsListView items only current
	// being displayed will be loading at any given time
	mArrayBlockingQueue.remove(object);
	mArrayBlockingQueue.offer(object);
    }
    private void preScaleImage(String path)
    {
	// Always start with a sample size of 1
	mOptions.inSampleSize = 1;
	mOptions.inJustDecodeBounds = true;
	BitmapFactory.decodeFile(path, mOptions);
	if (mOptions.outWidth >= 960 && mOptions.outWidth < 1600)
	{
	    mOptions.inSampleSize = 2;
	}
	else if (mOptions.outWidth >= 1600 && mOptions.outWidth < 1920)
	{
	    mOptions.inSampleSize = 3;
	}
	else if (mOptions.outWidth >= 1920)
	{
	    mOptions.inSampleSize = 4;
	}
	mOptions.inJustDecodeBounds = false;
    }
    private boolean checkNetworkState(Context context)
    {
	// Check every aspect of wifi and mobile connections
	mMobileInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
	mWifiInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
	mWiMaxInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIMAX);
	if ((mWiMaxInfo != null && mWiMaxInfo.isAvailable()) || (mWiMaxInfo != null && mWiMaxInfo.isConnected()) || (mWifiInfo != null && mWifiInfo.isAvailable()) || (mWifiInfo != null && mWifiInfo.isConnected()) || (mMobileInfo != null && mMobileInfo.isAvailable()) || (mMobileInfo != null && mMobileInfo.isConnected()))
	    return true;
	return false;
    }
    private void closeCursor(Cursor cursor)
    {
	if (cursor != null && !cursor.isClosed())
	    cursor.close();
    }
}