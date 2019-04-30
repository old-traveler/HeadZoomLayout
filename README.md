# HeadZoomLayout
[![](https://jitpack.io/v/old-traveler/HeadZoomLayout.svg)](https://jitpack.io/#old-traveler/HeadZoomLayout)&nbsp;&nbsp;
 [![AppVeyor](https://img.shields.io/badge/build-passing-green.svg)](https://github.com/old-traveler/HeadZoomLayout)

一个可以下拉放大头部背景图的布局控件，可轻松实现下拉放大头部背景图和QQ个人信息页面下拉扩展并放大背景图效果

非入侵式，不干扰业务代码、不影响子View的滑动。类似于官方的SwipeRefreshLayout

可包裹以下控件使用
* RecyclerView
* ListView
* ScrollView
* NestedScrollView
* LinearLayout等



## 效果展示


[![](https://raw.githubusercontent.com/old-traveler/HeadZoomLayout/master/image/show.gif)](https://github.com/old-traveler/HeadZoomLayout)


## 使用

定义布局时需要声明头部视图的id{@link HeadZoomLayout#headViewId}，
同时需要将头部中的背景图片（ImageView对象）scaleType设置为centerCrop

如需实现类似于QQ个人信息界面中下拉扩展并放大效果只需保证头部的宽高比大于图片的宽高比即可。


```groovy
allprojects {
	repositories {
		//...
		maven { url 'https://jitpack.io' }
	}
}
```

```groovy
dependencies {
	implementation 'com.github.old-traveler:HeadZoomLayout:Tag'
}
```

|属性名称|属性说明|默认值|
| ---- | ----- | ----|
|headViewId|头部视图id|0|
|maxZoomRatio|头部最大放大比例|1.0f|
|zoomEnable|是否可以放大头部|true|
|maxDragDistance|值越大，阻尼越大|1000f|
|dragAccelerationRatio|值越大，阻尼越大|3.0f|
|useDecelerateInterpolator|回弹动画使用减速插值器|true|
|maxRecoverTime|最大回弹动画时间|400L|


### 嵌套LinearLayout使用

```xml
<com.hyc.headzoomlayout.HeadZoomLayout
  android:layout_width="match_parent"
  android:layout_height="match_parent"
  app:headViewId="@id/fl_head">
  <!--嵌套LinearLayout使用-->
  <LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical">
    <!--头部视图-->
    <FrameLayout
      android:id="@+id/fl_head"
      android:layout_width="match_parent"
      android:layout_height="150dp"
      android:background="@color/colorAccent">
      <!--给背景ImageView设置中心剪裁方式 android:scaleType="centerCrop"-->
      <ImageView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:scaleType="centerCrop"
        android:src="@mipmap/test" />
      //...
      
    </FrameLayout>
    
    //...
  </LinearLayout>
</com.hyc.headzoomlayout.HeadZoomLayout>

```

### 嵌套ScrollView或NestedScrollView

```xml
<com.hyc.headzoomlayout.HeadZoomLayout
  android:layout_width="match_parent"
  android:layout_height="match_parent"
  app:headViewId="@id/fl_head">
  <!--嵌套ScrollView或者NestedScrollView使用-->
  <ScrollView
     android:layout_width="match_parent"
     android:layout_height="match_parent">
     <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">
        <!--头部视图-->
        <FrameLayout
          android:id="@+id/fl_head"
          android:layout_width="match_parent"
          android:layout_height="150dp"
          android:background="@color/colorAccent">
          <!--给背景ImageView设置中心剪裁方式 android:scaleType="centerCrop"-->
          <ImageView
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:scaleType="centerCrop"
            android:src="@mipmap/test" />
          //...
          
        </FrameLayout>
        
        //...
     </LinearLayout>
  </ScrollView>
</com.hyc.headzoomlayout.HeadZoomLayout>

```

### 嵌套RecyclerView或ListView

使用动态添加Head的形式并将其id设置HeadZoomLayout中headViewId
or
多样式布局时设置第一个Item的布局id为HeadZoomLayout中的headViewId

```xml
<!--主布局-->
<com.hyc.headzoomlayout.HeadZoomLayout
  app:headViewId="@id/fl_head"
  android:layout_width="match_parent"
  android:layout_height="match_parent">
  
  <ListView
    android:layout_width="match_parent"
    android:layout_height="match_parent"/>
  
</com.hyc.headzoomlayout.HeadZoomLayout>

<!--head布局，在代码中添加到ListView或者RecyclerView中-->
<FrameLayout
   android:id="@+id/fl_head"
   android:layout_width="match_parent"
   android:layout_height="150dp"
   android:background="@color/colorAccent">
   <!--给背景ImageView设置中心剪裁方式 android:scaleType="centerCrop"-->
   <ImageView
     android:layout_width="match_parent"
     android:layout_height="match_parent"
     android:scaleType="centerCrop"
     android:src="@mipmap/test" />
   //...  
</FrameLayout>
```






