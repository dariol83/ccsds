/*
 *  Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package eu.dariolucia.ccsds.sle.utl.config.network;

import javax.xml.bind.DatatypeConverter;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

import eu.dariolucia.ccsds.sle.utl.si.AuthenticationModeEnum;
import eu.dariolucia.ccsds.sle.utl.si.HashFunctionEnum;

import java.util.Objects;

@XmlAccessorType(XmlAccessType.FIELD)
public class RemotePeer {

	@XmlAttribute(name = "id")
	private String id = null;
	
	@XmlAttribute(name = "auth-mode")
	private AuthenticationModeEnum authenticationMode = AuthenticationModeEnum.NONE;

	@XmlAttribute(name = "auth-hash")
	private HashFunctionEnum authenticationHash = HashFunctionEnum.SHA_1;

	@XmlAttribute(name = "password")
	private String password = null;

	public RemotePeer() {
	}

	public RemotePeer(String id, AuthenticationModeEnum authenticationMode, HashFunctionEnum authenticationHash, String password) {
		this.id = id;
		this.authenticationMode = authenticationMode;
		this.authenticationHash = authenticationHash;
		this.password = password;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public AuthenticationModeEnum getAuthenticationMode() {
		return authenticationMode;
	}

	public void setAuthenticationMode(AuthenticationModeEnum authenticationMode) {
		this.authenticationMode = authenticationMode;
	}

	public byte[] getPassword() {
		return DatatypeConverter.parseHexBinary(password);
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public HashFunctionEnum getAuthenticationHash() {
		return authenticationHash;
	}

	public void setAuthenticationHash(HashFunctionEnum authenticationHash) {
		this.authenticationHash = authenticationHash;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		RemotePeer that = (RemotePeer) o;
		return Objects.equals(id, that.id) &&
				authenticationMode == that.authenticationMode &&
				authenticationHash == that.authenticationHash &&
				Objects.equals(password, that.password);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, authenticationMode, authenticationHash, password);
	}
}
