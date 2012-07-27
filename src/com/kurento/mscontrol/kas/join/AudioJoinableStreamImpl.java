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

package com.kurento.mscontrol.kas.join;

import javax.sdp.SdpException;

import android.util.Log;

import com.kurento.commons.media.format.conversor.SdpConversor;
import com.kurento.kas.media.codecs.AudioCodecType;
import com.kurento.kas.media.profiles.AudioProfile;
import com.kurento.kas.media.rx.AudioRx;
import com.kurento.kas.media.rx.AudioSamples;
import com.kurento.kas.media.rx.MediaRx;
import com.kurento.kas.media.tx.AudioInfoTx;
import com.kurento.kas.media.tx.AudioSamplesTx;
import com.kurento.kas.media.tx.MediaTx;
import com.kurento.mediaspec.MediaType;
import com.kurento.mediaspec.SessionSpec;
import com.kurento.mscontrol.commons.MsControlException;
import com.kurento.mscontrol.commons.join.Joinable;
import com.kurento.mscontrol.commons.join.JoinableContainer;
import com.kurento.mscontrol.kas.mediacomponent.internal.AudioSink;
import com.kurento.mscontrol.kas.networkconnection.internal.RTPInfo;

public class AudioJoinableStreamImpl extends JoinableStreamBase implements AudioSink, AudioRx {

	public final static String LOG_TAG = "AudioJoinableStream";

	private AudioInfoTx audioInfo;
	private SessionSpec localSessionSpec;

	private AudioRxThread audioRxThread = null;

	private long timeFirstSamples;
	private long timeLastSamples;

	private int audioPacketTime; // ms
	private long n;

	public AudioInfoTx getAudioInfoTx() {
		return audioInfo;
	}

	public AudioJoinableStreamImpl(JoinableContainer container,
			StreamType type, SessionSpec remoteSessionSpec,
			SessionSpec localSessionSpec, Integer maxDelayRx) {
		super(container, type);
		this.localSessionSpec = localSessionSpec;

		RTPInfo remoteRTPInfo = new RTPInfo(remoteSessionSpec);
		com.kurento.mediaspec.Direction audioMode = remoteRTPInfo
				.getAudioMode();

		if (audioMode != null
				&& !com.kurento.mediaspec.Direction.INACTIVE.equals(audioMode)) {
			AudioCodecType audioCodecType = remoteRTPInfo.getAudioCodecType();
			AudioProfile audioProfile = AudioProfile
					.getAudioProfileFromAudioCodecType(audioCodecType);

			if (audioProfile != null) {
				audioInfo = new AudioInfoTx(audioProfile);
				audioInfo.setOut(remoteRTPInfo.getAudioRTPDir());
				audioInfo.setPayloadType(remoteRTPInfo.getAudioPayloadType());

				if (com.kurento.mediaspec.Direction.SENDRECV.equals(audioMode)
						|| com.kurento.mediaspec.Direction.RECVONLY
								.equals(audioMode)) {
					audioInfo.setFrameSize(MediaTx.initAudio(audioInfo));
					if (audioInfo.getFrameSize() < 0) {
						Log.e(LOG_TAG, "Error in initAudio");
						MediaTx.finishAudio();
						return;
					}
				}

				if ((com.kurento.mediaspec.Direction.SENDRECV.equals(audioMode) || com.kurento.mediaspec.Direction.SENDONLY
						.equals(audioMode))) {
					this.audioRxThread = new AudioRxThread(this, maxDelayRx);
					this.audioRxThread.start();
				}

				this.audioPacketTime = (1000 * audioInfo.getFrameSize())
						/ audioInfo.getAudioProfile().getSampleRate();
				this.timeFirstSamples = -1;
			}
		}
	}

	@Override
	public AudioSamplesTx putAudioSamples(short[] data, int size, long time) {
		if (timeFirstSamples == -1) {
			n = 0;
			timeFirstSamples = time;
		}
		long diffFirstFrame = time - timeFirstSamples;
		long drift = n - diffFirstFrame;
		if (drift < -2 * audioPacketTime) {
			Log.w(LOG_TAG, "Audio TX gap. Drift: " + drift);
			n = diffFirstFrame - (diffFirstFrame % audioPacketTime);
		}

		AudioSamplesTx as = new AudioSamplesTx(data, size, n);
		int nBytes = MediaTx.putAudioSamples(as);
		computeOutBytes(nBytes);

		timeLastSamples = time;
		n += audioPacketTime;

		return as;
	}

	@Override
	public void putAudioSamplesRx(AudioSamples audioSamples) {
		computeInBytes(audioSamples.getEncodedSize());
		try {
			for (Joinable j : getJoinees(Direction.SEND))
				if (j instanceof AudioRx)
					((AudioRx) j).putAudioSamplesRx(audioSamples);
		} catch (MsControlException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void stop() {
		Log.d(LOG_TAG, "finishAudio");
		MediaTx.finishAudio();
		Log.d(LOG_TAG, "stopAudioRx");
		MediaRx.stopAudioRx();
	}

	private class AudioRxThread extends Thread {
		private AudioRx audioRx;
		private int maxDelayRx;

		public AudioRxThread(AudioRx audioRx, int maxDelayRx) {
			this.audioRx = audioRx;
			this.maxDelayRx = maxDelayRx;
		}

		@Override
		public void run() {
			Log.d(LOG_TAG, "startAudioRx");
			SessionSpec s = filterMediaByType(localSessionSpec, MediaType.AUDIO);
			if (!s.getMedias().isEmpty()) {
				try {
					String sdpAudio = SdpConversor.sessionSpec2Sdp(s);
					MediaRx.startAudioRx(sdpAudio, maxDelayRx, this.audioRx);
				} catch (SdpException e) {
					Log.e(LOG_TAG, "Could not start audio rx " + e.toString());
				}
			}
		}
	}

}
