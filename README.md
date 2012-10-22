LazyLoadingAdapter
==================

This project is used in Shady Photo & Video Safe 4.0+ live on the Play Store: <a href="https://play.google.com/store/apps/details?id=com.project.memoryerrorsafetwo">Shady Photo & Video Safe 4.0+</a>

<img src="https://lh3.ggpht.com/CVH-reFVv4KGr6JBzX6RY8hiSDBceH6TD9F13W1jpc9zPGiYtNiPkihCwC3ZMEVSxQ=w124"/>

A highly customizable and extendable lazy loading adapter class that has full javadoc. This project has countless customization methods, strategic callbacks, and a built in LRU caching system and support for position highlighting.

The class takes a type param specifying the type of literal supplying the images. Meaning if it's a path it'll be a String, if its a thumbnail ID it'll be a Long, and if it's an http address it'll be a Uri.
<br><br>
The constructor takes 7 params:
<br>
1) context 
<br>
2) view  - Your AbsListView or your Gallery Widget
<br>
3) height - The height of your images
<br>
4) width - The width of your images
<br>
5) pathsIDsOrUris - A List of Strings, Long IDs for "Thumbnails.getThumbnail()" from the phone's Image/Video content provider, or URIs of http addresses
<br>
6) size - The size of the LRU cache in megabytes
<br>
7) isImages - If the adapter will be loading thumbnails from image files or video files

The constructor throws UnsupportedContentException - Per design only strings of local paths, Longs of thumb IDs, or URIs of remote media are supported

<B> Example </B>

<code>float scale = getResources().getDisplayMetrics().density;<br>
new LazyLoadingAdapter\<String\>(mActivity, mGrid, (int) (scale * 63), (int) (scale * 63), null, 4, true);</code>

You can pass null as the List gracefully. Then you would use setPathsIDsOrUris(List<E> pathsIdsOrUris) and notifyDataSetChanged() to update your data.
You'll also want to call clearCache() after changing the List of data before you call notifyDataSetChanged(). You may also want to use clearCacheWithCallback() if your 
loading media from remote locations; the library will cache remotely downloaded media to the app's cache directory.
Also:


<code>float scale = getResources().getDisplayMetrics().density;<br>
ArrayList\<String\> thumbPaths = new ArrayList\<String\>();<br>
thumbPaths.add(getFilesDir() + "/cheese1.jpg");<br>
thumbPaths.add(getFilesDir() + "/cheese2.jpg");<br>
thumbPaths.add(getFilesDir() + "/cheese3.jpg");<br>
new LazyLoadingAdapter\<String\>(mActivity, mGrid, (int) (scale * 63), (int) (scale * 63), thumbPaths, 4, true);</code>

<B> WARNING </B>

!!!!!You MUST close the adapter. You will leak the loader thread if the adapter is not closed in an onDestroy or wherever most applicable!!!!

<B> COPYRIGHT </B>

Copyright 2012 Noah Seidman

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.