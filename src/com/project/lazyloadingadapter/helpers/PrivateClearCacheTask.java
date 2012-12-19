package com.project.lazyloadingadapter.helpers;
import java.io.File;
import android.content.Context;
import android.os.AsyncTask;
import com.project.lazyloadingadapter.objects.ClearCacheCallback;
public class PrivateClearCacheTask extends AsyncTask<Void, Void, Void>
{
    private Context mContext;
    private ClearCacheCallback mClearCacheCallback;
    public PrivateClearCacheTask(Context context, ClearCacheCallback clearCacheCallback)
    {
	mContext = context;
	mClearCacheCallback = clearCacheCallback;
    }
    @Override
    protected Void doInBackground(Void... params)
    {
	try
	{
	    File cacheDirectory = mContext.getCacheDir();
	    for (File file : cacheDirectory.listFiles())
	    {
		if (file != null && file.exists())
		    file.delete();
	    }
	}
	catch (Exception e)
	{
	    e.printStackTrace();
	    // I don't trust this ish
	}
	return null;
    }
    @Override
    public void onPostExecute(Void nada)
    {
	mClearCacheCallback.complete();
    }
}