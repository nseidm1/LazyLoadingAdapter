package com.project.lazyloadingadapter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v4.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ViewSwitcher;
import com.project.lazyloadingadapter.objects.QueueObject;
import com.project.lazyloadingadapter.objects.UnsupportedContentException;
import com.project.lazyloadingadapter.support.RetrieverThread;
import com.project.lazyloadingadapter.support.RetrieverThread.AltImageLoadListener;
/**
 * @author Noah Seidman
 */
@SuppressWarnings("deprecation")
public class LazyLoadingAdapter<E> extends BaseAdapter implements AltImageLoadListener<E>
{
    private Context mContext;
    private int mWidth;
    private int mHeight;
    private int mHightlightColor = Color.YELLOW;
    private ArrayList<E> mPathsIDsOrUris = new ArrayList<E>();
    private List<Integer> mAddHighlight;
    private ViewHolder holder;
    protected View mView;
    private boolean mIsImages;
    protected Handler mHandler = new Handler();
    protected RetrieverThread<E> mAltImageRetrieverThread;
    protected LruCache<Object, Bitmap> mCache;
    protected static final int PROGRESSBARINDEX = 0;
    protected static final int IMAGEVIEWINDEX = 1;
    private int mDegressRotation = 0;
    /**
     * A lazy loading image adapter using an array blocking queue.This
     * adapter works well with images of any size. 
     * <p>
     * It gracefully accommodates the memory requirements of image decoding implementing a
     * prescaling algorithm when required and using a
     * seamless out of memory accommodation technique. The
     * adapter supports AbsListViews which are ListViews and GridViews, as
     * well as Gallery widgets.
     * <p>
     * Provide this adapter with a reference to the AbsListView or Gallery,
     * the height/width of the desired images, a Object List of the image
     * paths/ThumbID (may be URIs of remote http locations), and a size for the
     * built in LRU cache in Megabytes
     * <p>
     * The Object list can contain 3 object types. Supply Longs if you want
     * the IDs of Gallery thumbnails. Supply strings if you want the full
     * image/video paths on the local filesystem. For videos make sure the
     * path contains the full extension of the video! Supply Uris if you
     * want remote images from the internet. Curerntly Uris will default to
     * image decoding and do not support videos!
     * <p>
     * DON'T forger to close() this adapter to stop the lazy loading
     * thread!!! If forgotten it will leak bad! You should also cleanup the
     * cache directory, on occasion, if your using remote data.
     * @param context
     * @param view
     * Your AbsListView or your Gallery Widget
     * @param height
     * The height of your image
     * @param width
     * The width of your image
     * @param pathsOrIds
     * A List of Strings, Long IDs for "Thumbnails.getThumbnail()" from the phone's Image/Video content provider, or URIs of http addresses
     * @throws UnsupportedContentException
     * Per design only strings of local paths, Longs of thumb IDs, or URIs of remote media are supported
     */
    public LazyLoadingAdapter(Context context, View view, int height, int width, ArrayList<E> pathsIDsOrUris, int size, boolean isImages) throws UnsupportedContentException
    {
	if (pathsIDsOrUris != null)
	{
	    testData(pathsIDsOrUris);
	    mPathsIDsOrUris = pathsIDsOrUris;
	}
	testView(view);
	mContext = context;
	mView = view;
	mCache = new LruCache<Object, Bitmap>(size * 1024 * 1024)
	{
	    @Override
	    protected int sizeOf(Object key, Bitmap value)
	    {
		return value.getRowBytes() * value.getHeight();
	    }
	};
	mWidth = width;
	mHeight = height;
	mAddHighlight = new ArrayList<Integer>();

	mIsImages = isImages;
	// Provide the loader thread with some info beforehand including the
	// cache, width, and height. The dimensions of the
	// desired thumbnail are needed for decoding purposes
	mAltImageRetrieverThread = new RetrieverThread<E>(mContext, mHandler, this, mCache, mWidth, mHeight, mIsImages);
	mAltImageRetrieverThread.start();
    }
    private void testData(List<?> pathsOrIds) throws UnsupportedContentException
    {
	for (Object object : pathsOrIds)
	{
	    if (!(object instanceof Long) && !(object instanceof String) && !(object instanceof Uri))
	    {
		throw new UnsupportedContentException("List content contains unsupported data. " + "This adapter accepts a List of String paths to images or video, "
			+ "Long values referring to the thumbnail of a Gallery image, or Uri " + "locations of remote content.");
	    }
	}
    }
    private void testView(View view) throws UnsupportedContentException
    {
	if (!(view instanceof Gallery) && !(view instanceof AbsListView))
	{
	    throw new UnsupportedContentException("The supplied view can only be an AbsListView or a Gallery");
	}
    }
    /**
     * @param pathOrId
     * The specific String path, Long id, or Uri you want to remove from the cache.
     */
    public void removeFromCache(E pathIDOrUri)
    {
	mCache.remove(pathIDOrUri);
    }
    /**
     * @param height
     * Specify the height of the view your adapter will be filling.
     */
    public void setImageHeight(int height)
    {
	mHeight = height;
    }
    /**
     * @param width
     * Specify the width of the view your adapter will be filling.
     */
    public void setImageWidth(int width)
    {
	mWidth = width;
    }
    /**
     * @param position
     * Add the specified highlight color to the position. The default highlight color is yellow, use setHighlightColor() to change the value.
     */
    public void addHighlight(int position)
    {
	mAddHighlight.add(position);
    }

    /**
     * @param highlightColor
     * Supply a Color value or use Color.Parse() to use a custom hex value.
     */
    public void setHighlightColor(int highlightColor)
    {
	mHightlightColor = highlightColor;
    }
    /**
     * @param position
     * Remove the highlight from the specific item position.
     */
    public void removeHighlight(int position)
    {
	mAddHighlight.remove(position);
    }
    /**
     * Clear all highlighted position.
     */
    public void clearHighlights()
    {
	mAddHighlight.clear();
    }
    /**
     * @param pathsOrIds
     * A List of Strings, Long IDs for "Thumbnails.getThumbnail()" from the phone's Image/Video content provider, or URIs of http addresses
     * @throws UnsupportedContentException
     * Per design only strings of local paths, Longs of thumb IDs, or URIs of remote media are supported
     */
    public void setPathsOrIds(ArrayList<E> pathsIDsOrUris) throws UnsupportedContentException
    {
	testData(pathsIDsOrUris);
	mPathsIDsOrUris = pathsIDsOrUris;
    }
    /**
     * @param view
     * Your AbsListView or your Gallery Widget
     * @throws UnsupportedContentException
     * Per design only strings of local paths, Longs of thumb IDs, or URIs of remote media are supported
     */
    public void setView(View view) throws UnsupportedContentException
    {
	testView(view);
	mView = view;
    }
    @Override
    public int getCount()
    {
	return mPathsIDsOrUris.size();
    }
    @Override
    public E getItem(int position)
    {
	return mPathsIDsOrUris.get(position);
    }
    /**
     * This is absolutely mandatory to use in onDestroy() or wherever otherwise applicable or appropriate. If this is not called you will leak the retriever thread. Leakie is no goodie.
     */
    public void close()
    {
	mAltImageRetrieverThread.stopThread();
    }
    public interface ClearCacheCallback
    {
	public void complete();
    }
    /**
     * @param clearCacheCallback
     */
    public void clearCacheWithCallback(ClearCacheCallback clearCacheCallback)
    {
	new PrivateClearCacheTask(clearCacheCallback).execute();
    }
    public void clearCache() throws IOException, NullPointerException
    {
	new PrivateClearCacheTask(null).execute();
    }
    /**
     * @param degrees
     * Supply the degress to rotate all the supplied images.
     */
    public void setRotation(int degrees)
    {
	mDegressRotation = degrees;
    }
    /**
     * @return
     * Get the currently set value for the rotation to apply.
     */
    public int getRotation()
    {
	return mDegressRotation;
    }
    private class PrivateClearCacheTask extends AsyncTask<Void, Void, Void>
    {
	private ClearCacheCallback mClearCacheCallback;
	public PrivateClearCacheTask(ClearCacheCallback clearCacheCallback)
	{
	    mClearCacheCallback = clearCacheCallback;
	}
	@Override
	protected Void doInBackground(Void... params)
	{
	    mCache.evictAll();
	    File cacheDirectory = mContext.getCacheDir();
	    for (File file : cacheDirectory.listFiles())
	    {
		file.delete();
	    }
	    return null;
	}
	@Override
	public void onPostExecute(Void nada)
	{
	    if (mClearCacheCallback != null)
		mClearCacheCallback.complete();
	}
    }
    @Override
    public long getItemId(int position)
    {
	return position;
    }
    protected class ViewHolder
    {
	ImageView image;
    }
    @SuppressWarnings("unchecked")
    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
	if (convertView == null)
	{
	    convertView = processConvertView(convertView);
	}
	else
	{
	    holder = (ViewHolder)convertView.getTag();
	}
	// Retrieve the image either from the cache or load it into the cache,
	// then display accordingly
	processHighlights(position);
	retrieveImage((ViewSwitcher) convertView, position);
	return convertView;
    }
    protected void processHighlights(int position)
    {
	if (mAddHighlight.contains(position))
	{
	    holder.image.setPadding(5, 5, 5, 5);
	    holder.image.setBackgroundColor(mHightlightColor);
	}
	else
	{
	    holder.image.setPadding(0, 0, 0, 0);
	    holder.image.setBackgroundColor(Color.parseColor("#00000000"));
	}
    }
    protected Bitmap processRotation(Bitmap bitmap)
    {
	if (getRotation() == 0 && (getRotation() % 360) == 0)
	{
	    return bitmap;
	}
	Matrix matrix = new Matrix();
	matrix.setRotate(getRotation(), bitmap.getWidth() / 2, bitmap.getHeight() / 2);
	return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
    }
    protected void retrieveImage(ViewSwitcher convertView, int position)
    {
	// To accommodate data change race conditions only process the view of
	// the size of the data list of > the position requested
	if (mPathsIDsOrUris.size() != 0 && mPathsIDsOrUris.size() > position)
	{
	    convertView.setDisplayedChild(LazyLoadingAdapter.PROGRESSBARINDEX);
	    mAltImageRetrieverThread.loadImage(new QueueObject<E>(position, mPathsIDsOrUris.get(position), convertView, holder.image));
	}
    }
    protected View processConvertView(View convertView)
    {
	holder = new ViewHolder();
	convertView = new ViewSwitcher(mContext);
	LinearLayout progressBarContainer = new LinearLayout(mContext);
	progressBarContainer = initProgressBarContainer(progressBarContainer);
	holder.image = new ImageView(mContext);
	holder.image.setScaleType(ScaleType.FIT_CENTER);
	holder.image.setLayoutParams(new LinearLayout.LayoutParams(mWidth, mWidth));
	((ViewSwitcher) convertView).addView(progressBarContainer);
	((ViewSwitcher) convertView).addView(holder.image);
	convertView.setTag(holder);
	return convertView;
    }
    // Add a progress bar to the progress bar container and ensure that it's
    // centered accordingly
    protected LinearLayout initProgressBarContainer(LinearLayout progressBarContainer)
    {
	progressBarContainer.setLayoutParams(new LinearLayout.LayoutParams(mWidth, mHeight));
	progressBarContainer.setGravity(Gravity.CENTER);
	ProgressBar progress = new ProgressBar(mContext, null, android.R.attr.progressBarStyleSmall);
	progress.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
	progressBarContainer.addView(progress);
	return progressBarContainer;
    }
    @Override
    public void updateImageInUI(final QueueObject<E> object, final Bitmap image)
    {
	mHandler.post(new Runnable()
	{
	    public void run()
	    {
		// If the absView is scrolling fast by the time the image is
		// retrieved it may no longer be on the screen
		// This will alleviate posting an image to the respective view
		// if the position is no longer visible
		if (mView instanceof AbsListView && object.getPosition() >= ((AbsListView) mView).getFirstVisiblePosition() && object.getPosition() <= ((AbsListView) mView).getLastVisiblePosition())
		{
		    object.getImage().setImageBitmap(processRotation(image));
		    object.getViewSwitcher().setDisplayedChild(IMAGEVIEWINDEX);
		}
		else if (mView instanceof Gallery)
		{
		    object.getImage().setImageBitmap(processRotation(image));
		    object.getViewSwitcher().setDisplayedChild(IMAGEVIEWINDEX);
		}
	    }
	});
    }
}