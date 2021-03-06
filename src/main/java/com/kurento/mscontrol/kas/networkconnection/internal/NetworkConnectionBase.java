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

package com.kurento.mscontrol.kas.networkconnection.internal;

import java.net.InetAddress;

import com.kurento.mediaspec.SessionSpec;
import com.kurento.mscontrol.commons.MsControlException;
import com.kurento.mscontrol.commons.networkconnection.NetworkConnection;
import com.kurento.mscontrol.commons.networkconnection.SdpPortManager;
import com.kurento.mscontrol.kas.join.JoinableContainerImpl;

public abstract class NetworkConnectionBase extends JoinableContainerImpl
		implements NetworkConnection {

	protected SdpPortManager sdpPortManager;

	protected NetworkConnectionBase() {
		sdpPortManager = new SdpPortManagerImpl(this);
	}

	@Override
	public SdpPortManager getSdpPortManager() throws MsControlException {
		return sdpPortManager;
	}

	/**
	 * Gets a session template copy with all medias capacities and ports
	 * assigned.
	 * 
	 * @return
	 */
	public abstract SessionSpec generateSessionSpec() throws MsControlException;

	/**
	 * Indicates own medias and ports assigned. Warning: It could has
	 * inconsistencies with template.
	 * 
	 * @param localSpec
	 */
	public abstract void setLocalSessionSpec(SessionSpec localSpec);

	/**
	 * Indicates other party medias and ports assigned.
	 * 
	 * @return
	 */
	public abstract void setRemoteSessionSpec(SessionSpec remote);

	public abstract InetAddress getLocalAddress();

	public abstract String getStunHost();

	public abstract Integer getStunPort();

}
