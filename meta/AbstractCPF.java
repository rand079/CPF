/*
 * AbstractCPF.java
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

package moa.classifiers.meta;

import com.github.javacliparser.IntOption;
import com.yahoo.labs.samoa.instances.Instance;

import moa.classifiers.drift.DriftDetectionMethodClassifierExt;
import moa.core.Measurement;
import moa.options.FlagOption;
import moa.options.FloatOption;

public class AbstractCPF extends DriftDetectionMethodClassifierExt {

	private static final long serialVersionUID = 1L;

	public FloatOption similarityBetweenModelsOnBufferOption = new FloatOption(
            "similarityMargin",
            'm',
            "The percentage identical results on a buffer to select a model as the new concept",
            0.95, 0, 1);
 
    public FlagOption fadeModelOption = new FlagOption(
            "fadeModels",
            'f',
            "A flag that causes models to disappear over time if not re-selected");
    
    public IntOption fadePointsOption = new IntOption(
            "fadePoints",
            'p',
            "The size of the buffer",
            15, 0, 1000);
    
	@Override
	public boolean isRandomizable() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public double[] getVotesForInstance(Instance inst) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void resetLearningImpl() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void trainOnInstanceImpl(Instance inst) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected Measurement[] getModelMeasurementsImpl() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void getModelDescription(StringBuilder out, int indent) {
		// TODO Auto-generated method stub
		
	}

	public void clearBuffer() {
		// TODO Auto-generated method stub
		
	}

	public int getMaxModels() {
		// TODO Auto-generated method stub
		return 0;
	}

	public int getNumDrifts() {
		// TODO Auto-generated method stub
		return 0;
	}

	public int getModelReuses() {
		// TODO Auto-generated method stub
		return 0;
	}

	public int getModelMerges() {
		// TODO Auto-generated method stub
		return 0;
	}

	public int getNewClassifiersCreated() {
		// TODO Auto-generated method stub
		return 0;
	}

	public double getMeanModels() {
		// TODO Auto-generated method stub
		return 0;
	}

	public int getModelFades() {
		// TODO Auto-generated method stub
		return 0;
	}

	public double getAverageBufferSize() {
		// TODO Auto-generated method stub
		return 0;
	}

}
