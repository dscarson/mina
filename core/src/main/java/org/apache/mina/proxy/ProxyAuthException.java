/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.mina.proxy;

import javax.security.sasl.SaslException;

/**
 * ProxyAuthException.java - This class extends {@link SaslException} and represents an
 * authentication failure to the proxy.
 * 
 * @author Edouard De Oliveira <a href="mailto:doe_wanted@yahoo.fr">doe_wanted@yahoo.fr</a>
 * @version $Id: $
 */
public class ProxyAuthException extends SaslException {
    private static final long serialVersionUID = -6511596809517532988L;

    public ProxyAuthException(String message) {
        super(message);
    }

    public ProxyAuthException(String message, Throwable ex) {
        super(message, ex);
    }
}