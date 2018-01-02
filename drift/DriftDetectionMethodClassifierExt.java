/*
 * DriftDetectionMethodClassifierExt.java
 * author: Robert William Anderson - The University of Auckland
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License.
 * 
 */

package moa.classifiers.drift;

import moa.classifiers.Classifier;

public class DriftDetectionMethodClassifierExt extends DriftDetectionMethodClassifier{

	private static final long serialVersionUID = 1L;

	public boolean getDrift(){
		return this.ddmLevel == DDM_OUTCONTROL_LEVEL;
	}
	
	public boolean getWarning(){
		return this.ddmLevel == DDM_WARNING_LEVEL;
	}
	
	public Classifier getNewClassifier(){
		return this.newclassifier;
	}
}
