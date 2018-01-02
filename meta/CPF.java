/*
 * CPF.java
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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import sizeof.agent.SizeOfAgent;
import com.github.javacliparser.IntOption;
import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.Instances;
import moa.classifiers.Classifier;
import moa.classifiers.core.driftdetection.ChangeDetector;
import moa.core.Utils;
import moa.options.FlagOption;

public class CPF extends AbstractCPF {

	private static final long serialVersionUID = 1L;
    
    public IntOption bufferSizeOption = new IntOption(
            "bufferSize",
            'b',
            "The minimum size of the buffer",
            60, 0, 1000);
    
    double similarityMargin;
    int bufferSize;
    int fadePoints;
    
	//no max on classifiers
    ArrayList<Classifier> classifierCollection = new ArrayList<Classifier>();
    Integer currentClassifier = 0;
    
    //classifier results to compare
    ArrayList<HashMap<Integer, double[]>> modelComparisonMeasurements = new ArrayList<HashMap<Integer, double[]>>();
    int ddmPriorLevel = 0;
    
    //counter for total comparison measurements 
    int numberInstances = 0;
    int totalBufferInstances = 0;
    int numDrifts = 0;
    int modelReuses = 0;
    int modelMerges = 0;
    int maxModels = 0;
    int currentModels = 0;
    
    //buffer for instances
    Instances buffer = new Instances();
    
    //objects for model fading
    boolean fadeModels;
    HashMap<Integer, Integer> modelFadeScores = new HashMap<Integer, Integer>();
    int modelsFaded = 0;
    
    public static final int DDM_BUILD_BUFFER = 3;
    
    @Override
    public void resetLearningImpl() {
        //this.newclassifier = this.classifier.copy();
    	this.classifier = ((Classifier) getPreparedClassOption(this.baseLearnerOption)).copy();
        this.driftDetectionMethod = ((ChangeDetector) getPreparedClassOption(this.driftDetectionMethodOption)).copy();
        this.newClassifierReset = false;
        
        //added to method
        this.similarityMargin = this.similarityBetweenModelsOnBufferOption.getValue();
        this.bufferSize = this.bufferSizeOption.getValue();
        this.fadePoints = this.fadePointsOption.getValue();
        this.buffer.delete();
        this.classifierCollection.clear();
        this.numberInstances = 0;
        this.totalBufferInstances = 0;
        this.modelsFaded = 0;
        this.numDrifts = 0;
        this.modelReuses = 0;
        this.modelMerges = 0;
        this.maxModels = 0;
        this.currentModels = 1;
        this.modelComparisonMeasurements.clear();
        
        //model management flags
        fadeModels = fadeModelOption.isSet() ? true : false;
        createModel();
    }
    
    //Run usual drift-detection but check for equivalent models on change
    @Override
    public void trainOnInstanceImpl(Instance inst) {
    	
    	//sometimes, we just want these instances to build a buffer to inform model selection
    	//so this method just stores incoming instances for this buffer
    	this.numberInstances++;
    	if(ddmLevel == DDM_BUILD_BUFFER){
    		buffer.add(inst);
    		if(buffer.size() >= bufferSize) this.getNextModel();
    		return;
    	}
        
        int trueClass = (int) inst.classValue();
        boolean prediction = Utils.maxIndex(classifierCollection.get(currentClassifier)
                .getVotesForInstance(inst)) == trueClass;
        this.driftDetectionMethod.input(prediction ? 0.0 : 1.0);
        this.ddmLevel = DDM_INCONTROL_LEVEL;
        if (this.driftDetectionMethod.getChange()) {
            this.ddmLevel = DDM_OUTCONTROL_LEVEL;
        }
        if (this.driftDetectionMethod.getWarningZone()) {
            this.ddmLevel = DDM_WARNING_LEVEL;
        }
        
        
        switch (this.ddmLevel) {
            case DDM_WARNING_LEVEL:
            	if(this.ddmLevel != this.ddmPriorLevel){
            		this.warningDetected++;
            		buffer.delete();
            	}
                buffer.add(inst);
                break;
            case DDM_OUTCONTROL_LEVEL:
            	buffer.add(inst);
                this.changeDetected++;
                numDrifts++;
                this.getNextModel();
                break;

            case DDM_INCONTROL_LEVEL:
            	((Classifier)this.classifierCollection.get(currentClassifier)).trainOnInstance(inst);
                break;
            	
            default:

        }
        ddmPriorLevel = ddmLevel;
    }
    
    private void getNextModel(){
    	//check we have buffer of at least 30 instances and top up with future instances if not
    	if(buffer.size() < bufferSize){
    		this.ddmLevel =  DDM_BUILD_BUFFER;
    		return;
    	}

    	totalBufferInstances += buffer.size();
    	currentClassifier = null;
    	
    	//get results per model on this comparison window
    	ArrayList<BitSet> thisBufferResults = new ArrayList<BitSet>();
    	
    	//get indices of current models
    	ArrayList<Integer> currentModels = new ArrayList<Integer>();
    	for(int i = 0; i < classifierCollection.size(); i++){
    		if(!(classifierCollection.get(i) == null))
    			currentModels.add(i);
    	}
    	
    	for(int i = 0; i < currentModels.size(); i++){
    		thisBufferResults.add(new BitSet(buffer.size()));
        	
    		for(int j = 0; j < buffer.size(); j++){
    			if (!((Classifier) classifierCollection.get(currentModels.get(i))).correctlyClassifies(buffer.get(j)))
    				thisBufferResults.get(i).set(j);
    		}
    	}
	    
    	for(int i = 0; i < currentModels.size(); i++){
    		for(int j = i + 1; j > i & j < currentModels.size(); j++){
    			
    			//ensure comparison tracking values have been initialised
    			if(modelComparisonMeasurements.get(currentModels.get(i)).get(currentModels.get(j)) == null)
    				modelComparisonMeasurements.get(currentModels.get(i)).put(currentModels.get(j), 
    						new double[]{0,0/*,Double.MAX_VALUE,Double.MAX_VALUE,Double.MAX_VALUE,Double.MIN_VALUE,Double.MIN_VALUE,Double.MIN_VALUE*/});
    			    			
    			BitSet difference = (BitSet) thisBufferResults.get(j).clone();
    			difference.xor(thisBufferResults.get(i));
   			
    			double seen_before = modelComparisonMeasurements.get(currentModels.get(i)).get(currentModels.get(j))[0];
    			double agreed_before = modelComparisonMeasurements.get(currentModels.get(i)).get(currentModels.get(j))[1];
    			double seen_this_buffer = buffer.size();
    			double agreed_this_buffer = buffer.size() - difference.cardinality();
    			
    			modelComparisonMeasurements.get(currentModels.get(i)).get(currentModels.get(j))[0] = seen_before + seen_this_buffer;
    			modelComparisonMeasurements.get(currentModels.get(i)).get(currentModels.get(j))[1] = agreed_before + agreed_this_buffer;
    		}
    	}
    	
    	//check if we found a good enough model, preferring older models
    	for(int i = 0; i < thisBufferResults.size(); i++){
    		//System.out.println("Model " + currentModels.get(i) + " results = " + (double)(buffer.size() - thisBufferResults.get(i).cardinality())/(double)buffer.size());
    		if((double)(buffer.size() - thisBufferResults.get(i).cardinality())/(double)buffer.size() >= similarityMargin){
    			this.currentClassifier = currentModels.get(i);
    			modelReuses++;
    			System.out.println("Using model " + currentClassifier);
    			break;
    		}
    	}
    	
		//if existing models aren't similar enough to a new model, add a new model
    	//train it on even instances in buffer
    	if(currentClassifier == null){
    		createModel();
    		currentModels.add(currentClassifier);
    		
    		BitSet newModelResults = new BitSet(buffer.size());
    		
    		//Here we have a double buffer and will initialise a new model on even instances before testing against odds
    		//train
    		for(int i = 0; i < buffer.size(); i = i + 2){
    			((Classifier)this.classifierCollection.get(currentClassifier)).trainOnInstance(buffer.get(i));
    		}
    		
    		//test
    		for(int i = 1; i < buffer.size(); i = i + 2){
    			if (!((Classifier) classifierCollection.get(currentClassifier)).correctlyClassifies(buffer.get(i)))
    				newModelResults.set(i);
    			((Classifier)this.classifierCollection.get(currentClassifier)).trainOnInstance(buffer.get(i));
    		}
    		
    		for(int i = 0; i < thisBufferResults.size(); i++){
    			BitSet difference = (BitSet) newModelResults.clone();
    			difference.xor(thisBufferResults.get(i));
    			//set training bits for new model to zero in difference bitset
    			for(int j = 0; j < buffer.size(); j = j + 2)
    				difference.clear(j);
    			modelComparisonMeasurements.get(currentModels.get(i)).put(currentModels.get(currentModels.size()-1), 
					new double[]{buffer.size()/2,buffer.size()/2 - difference.cardinality()});
    		}
    	}
    	
    	this.mergeModels(currentModels);
    	if (this.fadeModels) fadeModels(currentModels);
    	this.ddmLevel =  DDM_OUTCONTROL_LEVEL;
    	buffer.delete();
    	
    	this.maxModels = Math.max(this.currentModels, this.maxModels);
    }
    
    //If a model acts the same way as another model similarityMargin proportion of the time, 
    //merge the newer model with the older model. If model fading enables, older model gets newer model's fade score
	private void mergeModels(ArrayList<Integer> currentModels){
		for(int i = 0; i < currentModels.size(); i++){
			int modelA = currentModels.get(i);
			if(classifierCollection.get(modelA) == null) continue;

			for(int j = i + 1; j < currentModels.size(); j++){
				int modelB = currentModels.get(j);
				if(classifierCollection.get(modelB) == null) continue;

				if(modelComparisonMeasurements.get(modelA).get(modelB)[1]/modelComparisonMeasurements.get(modelA).get(modelB)[0] >= similarityMargin){
					System.out.println("Merged model " + modelB + " with " + modelA);
					modelMerges++;
					removeModel(modelB);
					
					if(fadeModels){
						modelFadeScores.put(modelA, modelFadeScores.get(modelA) + ((modelFadeScores.get(modelB) == null) ? 0 : modelFadeScores.get(modelB)));
						modelFadeScores.put(modelB, null);
					}
					
					if(currentClassifier == modelB) {
						currentClassifier = modelA;
						System.out.println("Using " + modelA + " since " + modelB + " has been merged.");
					}
					
				}
			}
		}
	}
	
	private void createModel(){
		this.currentModels = this.currentModels + 1;
		this.currentClassifier = classifierCollection.size();
        this.classifierCollection.add(((Classifier) getPreparedClassOption(this.baseLearnerOption)).copy());
        ((Classifier)this.classifierCollection.get(currentClassifier)).prepareForUse();
        this.modelComparisonMeasurements.add(new HashMap<Integer, double[]>());
        if(fadeModels) modelFadeScores.put(currentClassifier, 0);
	}
	
	private void removeModel(int modelToRemove){
		this.currentModels = this.currentModels - 1;
		classifierCollection.set(modelToRemove, null);
		modelComparisonMeasurements.set(modelToRemove,null);
	}
	
	private void fadeModels(ArrayList<Integer> currentModels){
		int score_to_add = fadePoints;
    	for(int i:currentModels){
    		if (i == currentClassifier){
    			modelFadeScores.put(currentClassifier, score_to_add 
    				+ ((modelFadeScores.get(currentClassifier) == null) ? 0 : modelFadeScores.get(currentClassifier))); //handles if model is new and not yet in modelFadeScores
    		} else if (modelFadeScores.get(i) != null){ // null is case where model has been merged
    			modelFadeScores.put(i, modelFadeScores.get(i) - 1);
    			if(modelFadeScores.get(i) == 0){
    				removeModel(i);
    				this.modelsFaded++;
    			}
    		}
    	}
	}
	
	@Override
    public double[] getVotesForInstance(Instance inst) {
        return this.classifierCollection.get(currentClassifier).getVotesForInstance(inst);
    }
	
	public boolean getWarning(){
		if(this.driftDetectionMethod.getWarningZone()) return true;
		return false;
	}
	
	public boolean getDrift(){
		if(this.ddmLevel == DDM_OUTCONTROL_LEVEL)
			return true;
		return false;
	}
	
	public int getNumModels(){
		int num = 0;
		for(int i = 0; i < this.classifierCollection.size(); i++)
			if(this.classifierCollection.get(i) != null)
				num++;
		return num;
	}
}
