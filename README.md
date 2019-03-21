# HeadZoomLayout
一个可以下拉放大头部背景图的布局控件

[![](https://jitpack.io/v/old-traveler/HeadZoomLayout.svg)](https://jitpack.io/#old-traveler/HeadZoomLayout)

## 使用示例


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

```xml
<com.hyc.headzoomlayout.HeadZoomLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:headViewId="@id/fl_head">
    <LinearLayout
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:orientation="vertical">
      //头部视图
      <FrameLayout
        android:id="@+id/fl_head"
        android:layout_width="match_parent"
        android:layout_height="150dp"
        android:background="@color/colorAccent">
        //给ImageView设置中心剪裁方式 android:scaleType="centerCrop"
        <ImageView
          android:layout_width="match_parent"
          android:layout_height="match_parent"
          android:scaleType="centerCrop"
          android:src="@mipmap/test" />

        <de.hdodenhof.circleimageview.CircleImageView
          android:id="@+id/profile_image"
          android:layout_width="96dp"
          android:layout_height="96dp"
          android:layout_gravity="center_horizontal|bottom"
          android:layout_marginBottom="35dp"
          android:src="@mipmap/ic_logo"
          app:civ_border_color="#F7F6F6"
          app:civ_border_width="2dp" />

        <TextView
          android:id="@+id/tv_phone"
          android:layout_width="wrap_content"
          android:layout_height="wrap_content"
          android:layout_gravity="center_horizontal|bottom"
          android:layout_marginBottom="5dp"
          android:textColor="@color/white"
          android:textSize="20sp"
          tools:text="152****1295" />

      </FrameLayout>
      
      //...
    </LinearLayout>

  </com.hyc.headzoomlayout.HeadZoomLayout>

```


