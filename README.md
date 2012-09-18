LazyLoadingAdapter
==================

This project is used in Shady Photo & Video Safe 4.0+ live on the Play Store: <a href="https://play.google.com/store/apps/details?id=com.project.memoryerrorsafetwo">Shady Photo & Video Safe 4.0+</a>

<img src="https://lh3.ggpht.com/CVH-reFVv4KGr6JBzX6RY8hiSDBceH6TD9F13W1jpc9zPGiYtNiPkihCwC3ZMEVSxQ=w124"/>

A highly customizable and extendable lazy loading adapter class. This project has countless customization methods, strategic callbacks, and a built in LRU caching system and support for position highlighting.

The constructor takes 5 params:

1) context 
<p>
2) view  - Your AbsListView or your Gallery Widget
<p>
3) height - The height of your image
<p>
4) width - The width of your image
<p>
5) pathsIDsOrUris - A List of Strings, Long IDs for "Thumbnails.getThumbnail()" from the phone's Image/Video content provider, or URIs of http addresses
<p>
6) size - The size of the LRU cache in megabytes
<p>
7) isImages - If the adapter will be loading thumbnails from image files or video files. If your List is Longs of IDs, this boolean tell the loader thread to target the Images or Videos thumbnail provider

The constructor throws UnsupportedContentException - Per design only strings of local paths, Longs of thumb IDs, or URIs of remote media are supported

<B> Example </B>

new LazyLoadingAdapter<String>(mActivity, mGrid, (int) (scale * 63), (int) (scale * 63), null, 4, true);

You can pass null as the List gracefully. Then you would use setPathsIDsOrUris(List<E> pathsIdsOrUris) and notifyDataSetChanged() to update your data.

<B> WARNING </B>

!!!!!You MUST close the adapter. You will leak the loader thread if the adapter is not closed in an onDestroy or wherever most applicable!!!!

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