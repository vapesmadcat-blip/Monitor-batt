cat > app/src/main/res/values/themes.xml << 'THEME'
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.BatteryMonitor" parent="Theme.MaterialComponents.DayNight.DarkActionBar">
            <item name="colorPrimary">@color/purple_500</item>
                    <item name="colorPrimaryVariant">@color/purple_700</item>
                            <item name="colorOnPrimary">@color/white</item>
                                    <item name="colorSecondary">@color/teal_200</item>
                                            <item name="colorSecondaryVariant">@color/teal_700</item>
                                                    <item name="colorOnSecondary">@color/black</item>
                                                    
                                                            <item name="android:colorBackground">@color/colorBackground</item>
                                                                    <item name="colorSurface">@color/colorSurface</item>
                                                                            <item name="colorOnSurface">@color/colorOnSurface</item>
                                                                            
                                                                                    <item name="android:statusBarColor">?attr/colorPrimaryVariant</item>
                                                                                    
                                                                                            <item name="android:popupMenuStyle">@style/PopupMenuStyle</item>
                                                                                                    <item name="android:spinnerStyle">@style/Widget.App.Spinner</item>
                                                                                                        </style>
                                                                                                        
                                                                                                            <style name="PopupMenuStyle" parent="Widget.AppCompat.PopupMenu">
                                                                                                                    <item name="android:popupBackground">?attr/colorSurface</item>
                                                                                                                        </style>
                                                                                                                        
                                                                                                                            <style name="Widget.App.Spinner" parent="Widget.AppCompat.Spinner">
                                                                                                                                    <item name="android:popupBackground">?attr/colorSurface</item>
                                                                                                                                        </style>
                                                                                                                                        </resources>
                                                                                                                                        THEME
                                                                                                                                        