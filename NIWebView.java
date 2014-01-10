/**
 * Created by Trent Ahrens on 11/20/2012
 *
 * NIWebView allows you to evaluate and get result of javascript from native code.
 */

package com.lolboxen.ni;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

public class NIWebView extends WebView {

	public static String NI_LOG_TAG = "com.lolboxen.NI";
	
	private NIWebViewSyncInterface syncInterface;

	public NIWebView(Context context) {
		super(context);
		setup();
	}

	public NIWebView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setup();
	}

	public NIWebView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setup();
	}
	
	private void setup()
	{
		getSettings().setJavaScriptEnabled(true);
		
		syncInterface = new NIWebViewSyncInterface();
		addJavascriptInterface(syncInterface, "NISyncInterface");
	}
	
	public String stringByEvaluatingJavaScript(String javascript)
	{
		String escapedJavascript = javascript.replaceAll("\\\\", "\\\\\\\\").replaceAll("\\\"", "\\\\\"");
		String finalCode = 
				"javascript:try { " +
					"NISyncInterface.didCompile();" +
					"NISyncInterface.setReturnValue(eval(\"" + escapedJavascript + "\"));" +
				"} catch(err) {" +
					"NISyncInterface.setReturnValue('');" +
				"}";
		Log.v(NI_LOG_TAG, finalCode);
		syncInterface.startLatches();
		this.loadUrl(finalCode);
		return syncInterface.getReturnValue();
	}
	
	private class NIWebViewSyncInterface
	{
		private CountDownLatch compileLatch;
		private CountDownLatch finishLatch;
		private String returnValue;
		
		public void startLatches()
		{
			compileLatch = new CountDownLatch(1);
			finishLatch = new CountDownLatch(1);
		}

		public String getReturnValue()
		{
			returnValue = "";
			
			try {
				if (compileLatch.await(1, TimeUnit.SECONDS) == false)
				{
					Log.e(NI_LOG_TAG, "script did not compile");
					return returnValue;
				}
			} catch (InterruptedException e) {
				return returnValue;
			}
			
			try {
				finishLatch.await(30, TimeUnit.SECONDS);
				return returnValue;
			} catch (InterruptedException e) {
				Log.e(NI_LOG_TAG, "Timed out waiting for JS response");
				throw new RuntimeException("Timed out waiting for JS response");
			}
		}
		
		@SuppressWarnings("unused")
		@JavascriptInterface
		public void setReturnValue(String returnValue)
		{
			this.returnValue = returnValue;
			finishLatch.countDown();
		}
		
		@SuppressWarnings("unused")
		@JavascriptInterface
		public void didCompile()
		{
			compileLatch.countDown();
		}
	}
}
