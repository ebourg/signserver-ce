/*************************************************************************
 *                                                                       *
 *  SignServer: The OpenSource Automated Signing Server                  *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/

package org.signserver.server.signtokens;

import java.util.Properties;

import javax.ejb.EJBException;

import org.apache.log4j.Logger;
import org.signserver.common.WorkerConfig;

import se.primeKey.caToken.card.PrimeCAToken;


/**
 * Class used to connect to the PrimeCard HSM card.
 * 
 * @see org.signserver.server.signtokens.ISignToken
 * @author Philip Vendil
 * @version $Id: PrimeCardHSMSignToken.java,v 1.5 2007-09-27 10:02:27 anatom Exp $
 */

public class PrimeCardHSMSignToken extends CATokenSignTokenBase implements ISignToken {

	private static final Logger log = Logger.getLogger(PrimeCardHSMSignToken.class);

	public PrimeCardHSMSignToken(){
		catoken = new PrimeCAToken();   
	}

	/**
	 * Method initializing the primecardHSM device 
	 * 
	 */
	public void init(Properties props) {
		log.debug(">init");
		String signaturealgoritm = props.getProperty(WorkerConfig.SIGNERPROPERTY_SIGNATUREALGORITHM);
		props = fixUpProperties(props);
		((PrimeCAToken)catoken).init(props, null, signaturealgoritm);	
		String authCode = props.getProperty("authCode");
		if(authCode != null){
			try{ 
				this.activate(authCode);
			}catch(Exception e){
				throw new EJBException(e);
			}
		}
		log.debug("<init");
	}

}
