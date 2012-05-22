/*
 * Kurento Android MSControl: MSControl implementation for Android.
 * Copyright (C) 2011  Tikal Technologies
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.kurento.kas.mscontrol.mediacomponent.internal;

import java.io.IOException;
import java.util.List;

import android.hardware.Camera;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.Build.VERSION;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;

import com.kurento.commons.mscontrol.MsControlException;
import com.kurento.commons.mscontrol.Parameters;
import com.kurento.commons.mscontrol.join.Joinable;
import com.kurento.kas.media.profiles.VideoProfile;
import com.kurento.kas.mscontrol.join.VideoJoinableStreamImpl;
import com.kurento.kas.mscontrol.mediacomponent.AndroidAction;

public class VideoPlayerComponent extends MediaComponentBase implements
		PreviewCallback {
	// SurfaceHolder.Callback, {

	private static final String LOG_TAG = "VideoPlayer";

	private SurfaceView mVideoView;
	// private SurfaceHolder mHolder;

	private Camera mCamera;
	private int cameraFacing = 0;
	private View videoSurfaceTx;

	private int width;
	private int height;
	private int screenOrientation;
	private SurfaceHolder mHolder2;
	private Callback cb;
	private static boolean isMholderCreated = false;
	private boolean isReleased;

	public View getVideoSurfaceTx() {
		return videoSurfaceTx;
	}

	@Override
	public boolean isStarted() {
		return mCamera != null;
	}

	public VideoPlayerComponent(Parameters params) throws MsControlException {
		if (params == null)
			throw new MsControlException("Parameters are NULL");

		final View sv = (View) params.get(PREVIEW_SURFACE);
		if (sv == null)
			throw new MsControlException(
					"Params must have VideoPlayerComponent.PREVIEW_SURFACE param");

		videoSurfaceTx = sv;
		screenOrientation = (Integer) params.get(DISPLAY_ORIENTATION) * 90;
		isReleased = false;
		try {
			cameraFacing = (Integer) params.get(CAMERA_FACING);
		} catch (Exception e) {
			cameraFacing = 0;
		}
		Log.d(LOG_TAG, "VideoPlayerComponent " + videoSurfaceTx.toString());

	}

	//TODO: Review orientation camera when you use front camera.
	private Camera openFrontFacingCameraGingerbread() {
		int cameraCount = 0;
		Camera cam = null;
		Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
		cameraCount = Camera.getNumberOfCameras();
		Log.d(LOG_TAG, "Num Camera is " + cameraCount
				+ ". User wants Camera = " + cameraFacing);
		// TODO: if only have one camera, open camera 0.
		if (cameraCount == 1) {
			try {
				cam = Camera.open(0);
			} catch (RuntimeException e) {
				Log.e(LOG_TAG,
						"Camera failed to open: " + e.getLocalizedMessage()
								+ " ; " + e.toString());
			}
		} else {
			for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
				Camera.getCameraInfo(camIdx, cameraInfo);
				if (cameraInfo.facing == cameraFacing) {// Camera.CameraInfo.CAMERA_FACING_BACK)
					try {
						cam = Camera.open(camIdx);
					} catch (RuntimeException e) {
						Log.e(LOG_TAG,
								"Camera failed to open: "
										+ e.getLocalizedMessage() + " ; "
										+ e.toString());
					}
				}
			}
		}

		return cam;
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		if (data == null)
			return;
		long time = System.currentTimeMillis();
		// Send frame to subscribers
		try {
			for (Joinable j : getJoinees(Direction.SEND))
				if (j instanceof VideoSink)
					((VideoSink) j).putVideoFrame(data, width, height, time);
		} catch (MsControlException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void start() throws MsControlException {

		VideoProfile videoProfile = null;
		for (Joinable j : getJoinees(Direction.SEND))
			if (j instanceof VideoJoinableStreamImpl) {
				videoProfile = ((VideoJoinableStreamImpl) j).getVideoProfile();
			}
		if (videoProfile == null)
			throw new MsControlException("Cannot get video profile.");

		this.width = videoProfile.getWidth();
		this.height = videoProfile.getHeight();

		mVideoView = (SurfaceView) videoSurfaceTx;
		Log.d(LOG_TAG, "Starting");

		mHolder2 = mVideoView.getHolder();
		mHolder2.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		if (isMholderCreated)
			startCamera();

		cb = new Callback() {
			public void surfaceDestroyed(SurfaceHolder holder) {
				Log.d(LOG_TAG, "Surface Destroy");
				if (mCamera != null) {
					mCamera.setPreviewCallback(null);
					mCamera.stopPreview();
					mCamera.release();
					isMholderCreated = false;
					mCamera = null;
				}
			}

			public void surfaceCreated(SurfaceHolder holder) {
				Log.d(LOG_TAG, "Surface Create");
				isMholderCreated = true;
				startCamera();
			}

			public void surfaceChanged(SurfaceHolder holder, int format,
					int width, int height) {
				Log.d(LOG_TAG, "Surface Changed");
				// mCamera.setPreviewCallback(VideoPlayerComponent.this);
			}
		};

		mHolder2.addCallback(cb);
	}

	private void startCamera() {
		if (isReleased)
			return;

		Log.d(LOG_TAG, "Start Camera");
		if (mCamera == null) {
			if (VERSION.SDK_INT < 9) {
				mCamera = Camera.open();
			} else
				mCamera = openFrontFacingCameraGingerbread();
		}
		mCamera.setErrorCallback(new ErrorCallback() {
			public void onError(int error, Camera camera) {
				Log.e(LOG_TAG, "Camera error : " + error);
			}
		});

		Log.d(LOG_TAG, "mCamera = " + mCamera.toString());
		Camera.Parameters parameters = mCamera.getParameters();

		List<Size> sizes = parameters.getSupportedPreviewSizes();
		// Video Preferences is support?
		boolean isSupport = false;
		int sizeSelected = -1;
		for (int i = 0; i < sizes.size(); i++) {
			if ((width == sizes.get(i).width)
					&& (height == sizes.get(i).height)) {
				isSupport = true;
				break;
			}
			if (sizeSelected == -1) {
				if (sizes.get(i).width <= width)
					sizeSelected = i;
			} else if ((sizes.get(i).width >= sizes.get(sizeSelected).width)
					&& (sizes.get(i).width <= width))
				sizeSelected = i;
		}
		if (sizeSelected == -1)
			sizeSelected = 0;
		if (!isSupport) {
			width = sizes.get(sizeSelected).width;
			height = sizes.get(sizeSelected).height;
		}
		parameters.setPreviewSize(width, height);
		mCamera.setParameters(parameters);

		String cad = "";
		for (int i = 0; i < sizes.size(); i++)
			cad += sizes.get(i).width + " x " + sizes.get(i).height + "\n";
		Log.d(LOG_TAG, "getPreviewSize: " + parameters.getPreviewSize().width
				+ " x " + parameters.getPreviewSize().height);
		Log.d(LOG_TAG, "getSupportedPreviewSizes:\n" + cad);

		try {

			mCamera.setPreviewDisplay(mHolder2);
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			mCamera.startPreview();
			mCamera.setPreviewCallback(VideoPlayerComponent.this);
		} catch (Throwable e) {
			Log.e(LOG_TAG, "Can't start camera preview");
		}

	}

	@Override
	public void stop() {
		Log.d(LOG_TAG, "Stop");
		if (mCamera != null) {
			mCamera.setPreviewCallback(null);
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
		Log.d(LOG_TAG, "mCamera release all");
	}

	@Override
	public void release() {
		Log.d(LOG_TAG, "Release");
		stop();
		isReleased = true;
		mHolder2.removeCallback(cb);
	}

	@Override
	public void onAction(AndroidAction action) throws MsControlException {
		if (action == null)
			throw new MsControlException("Action not supported");

		if (AndroidAction.CAMERA_AUTOFOCUS.equals(action)) {
			// TODO: autofocus camera.
		}

		throw new MsControlException("Action not supported");
	}

}
