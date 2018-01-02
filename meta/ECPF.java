/*
 * ECPF.java
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
import com.github.javacliparser.IntOption;
import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.Instances;
import moa.classifiers.Classifier;
import moa.classifiers.core.driftdetection.ChangeDetector;
import moa.core.Utils;

public class ECPF extends AbstractCPF{

	private static final long serialVersionUID = 1L;

	public IntOption modelCheckFreqOption = new IntOption(
            "modelCheckFreq",
            'c',
            "Frequency of comparing models (new vs. reused) to decide which to use for classifying incoming instances",
            1, 0, 100000);
    
	//no max on classifiers
	ArrayList<Classifier> classifierCollection = new ArrayList<Classifier>();
	Integer currentClassifier = 0;
	Classifier newModel = null;

	//buffer for instances
	Instances buffer = new Instances();
	
	double similarityMargin;
	int bufferSize;
	int fadePoints;
	int modelCheckFreq;
    int ddmPriorLevel = 0;
    
    //counters for measuring ECPF behaviour
    int numberInstances = 0;
    int totalBufferInstances = 0;
    int numDrifts = 0;
    int modelReuses = 0;
    int modelMerges = 0;
    int maxModels = 0;
    int currentModels = 0;
    int reuseFlag = 1;
    
    //Counters for classifier
	int currCorrect = 0;
	int newCorrect = 0;
	int totalInst = 0;
	
    // Object to hold measurements relating to model - accuracy and model similarity
    ArrayList<Integer[]> modelAccuracyMeasurements = new ArrayList<Integer[]>();
    ArrayList<HashMap<Integer, Integer[]>> modelComparisonMeasurements = new ArrayList<HashMap<Integer, Integer[]>>();
    
    //objects for model fading
    boolean fadeModels;
    HashMap<Integer, Integer> modelFadeScores = new HashMap<Integer, Integer>();
    int modelsFaded = 0;
    
    public static final int DDM_BUILD_BUFFER = 3;
    
	@Override
	public void resetLearningImpl() {
	
		this.classifier = ((Classifier) getPreparedClassOption(this.baseLearnerOption)).copy();
		this.driftDetectionMethod = ((ChangeDetector) getPreparedClassOption(this.driftDetectionMethodOption)).copy();

		this.similarityMargin = this.similarityBetweenModelsOnBufferOption.getValue();
		this.fadePoints = this.fadePointsOption.getValue();
		this.modelCheckFreq = this.modelCheckFreqOption.getValue();
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
		this.currCorrect = 0;
		this.newCorrect = 0;
		this.totalInst = 0;

	   //model management flags
	    fadeModels = fadeModelOption.isSet() ? true : false;
	    
	    addModel(((Classifier) getPreparedClassOption(this.baseLearnerOption)).copy());
	    this.classifierCollection.get(currentClassifier).prepareForUse();
	}
	
	private void addModel(Classifier newModel){
		this.currentModels = this.currentModels + 1;
		this.currentClassifier = classifierCollection.size();
	    this.classifierCollection.add(newModel);
	    this.modelAccuracyMeasurements.add(new Integer[] {0,0});
	    this.modelComparisonMeasurements.add(new HashMap<Integer, Integer[]>());
	    if(fadeModels) modelFadeScores.put(currentClassifier, 0);
	}
	
    //Run usual drift-detection but check for equivalent models on change
    @Override
    public void trainOnInstanceImpl(Instance inst) {
    	
    	this.numberInstances++;
        boolean prediction = getPrediction(inst);

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
                modelReuses++;
                compareClassifiers();
                this.getNextModel();
                break;

            case DDM_INCONTROL_LEVEL:
            	//System.out.println("DDM_INCONTROL_LEVEL");
            	trainClassifiers(inst);
                break;
            	
            default:
            	System.out.println("ERROR!");

        }
        ddmPriorLevel = ddmLevel;
    }
    
    private boolean getPrediction(Instance inst){
    	
    	int trueClass = (int) inst.classValue();
    	if(newModel != null){
    		boolean newPred = Utils.maxIndex(newModel
                    .getVotesForInstance(inst)) == trueClass;
    		if(newPred) newCorrect ++;
    	}
    	boolean currPred = Utils.maxIndex(classifierCollection.get(currentClassifier)
                .getVotesForInstance(inst)) == trueClass;
    	if (currPred)currCorrect++;
    	totalInst++;
    	
    	if(totalInst % modelCheckFreq == 0)
    		compareClassifiers();
    		
		return currPred;
    }
    
    private void trainClassifiers(Instance inst){
    	((Classifier)this.classifierCollection.get(currentClassifier)).trainOnInstance(inst);
    	if(newModel != null) newModel.trainOnInstance(inst);
    }
    
    private void compareClassifiers(){
    	if(currCorrect  < newCorrect){
    		int tempCorrect = currCorrect;
    		currCorrect = newCorrect;
    		Classifier temp = classifierCollection.get(currentClassifier);
    		classifierCollection.set(currentClassifier, newModel);
    		newModel = temp;
    		newCorrect = tempCorrect;
    		reuseFlag = reuseFlag * -1;
    	}
    }
    
    private void getNextModel(){
    	
    	//Update accuracy measurements with winning model
        this.modelAccuracyMeasurements.get(currentClassifier)[0] = this.modelAccuracyMeasurements.get(currentClassifier)[0] + totalInst;
        this.modelAccuracyMeasurements.get(currentClassifier)[1] = this.modelAccuracyMeasurements.get(currentClassifier)[1] + currCorrect;
    	
    	this.ddmLevel =  DDM_OUTCONTROL_LEVEL;
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
    						new Integer[]{0,0});
    			    			
    			BitSet difference = (BitSet) thisBufferResults.get(j).clone();
    			difference.xor(thisBufferResults.get(i)); 			
    			int seen_before = modelComparisonMeasurements.get(currentModels.get(i)).get(currentModels.get(j))[0];
    			int agreed_before = modelComparisonMeasurements.get(currentModels.get(i)).get(currentModels.get(j))[1];
    			int seen_this_buffer = buffer.size();
    			int agreed_this_buffer = buffer.size() - difference.cardinality();
    			
    			modelComparisonMeasurements.get(currentModels.get(i)).get(currentModels.get(j))[0] = seen_before + seen_this_buffer;
    			modelComparisonMeasurements.get(currentModels.get(i)).get(currentModels.get(j))[1] = agreed_before + agreed_this_buffer;
    		}
    	}
    	
    	//Merge similar models and simplify model results
    	
    	ArrayList<Integer> mergedModels = mergeModels(currentModels);
    	for(int i = 0; i < mergedModels.size(); i++){
    		int thisIndex = currentModels.indexOf(mergedModels.get(i));
    		currentModels.remove(thisIndex);
    		thisBufferResults.remove(thisIndex);    		
    	}
    	
		//add a new model to contend with existing models
    	//train it on even instances in buffer
		newModel = ((Classifier) getPreparedClassOption(this.baseLearnerOption)).copy();
		newModel.prepareForUse();
		BitSet newModelResults = new BitSet(buffer.size());
		
		//Here we have a double buffer and will initialise a new model on all warning zone instances
		//train new model
		for(int i = 0; i < buffer.size(); i++){
			newModel.trainOnInstance(buffer.get(i));
		}
		
    	//check older models to find best accuracy on buffer
    	double[] modelAccuracy = new double[thisBufferResults.size()];
    	int bestModelIndex = 0;
    	double maxAcc = 0;
		for(int i = 0; i < thisBufferResults.size(); i++){
    		modelAccuracy[i] = (double)(thisBufferResults.get(i).size() - thisBufferResults.get(i).cardinality())
    				/(double)(thisBufferResults.get(i).size());
    		if (modelAccuracy[i] > maxAcc){
    			bestModelIndex = i;
    			maxAcc = modelAccuracy[i];
    		}
    	}

		//Make copy of existing model to use
		addModel(classifierCollection.get(currentModels.get(bestModelIndex)).copy());
		currentModels.add(currentClassifier);
		
    	if (this.fadeModels) fadeModels(currentModels);
    	buffer.delete();
    	
    	this.maxModels = Math.max(this.currentModels, this.maxModels);
    	//System.out.println("Model selected: " + currentClassifier);
    	
    	//Reset counters for next concept
    	currCorrect = 0;
    	newCorrect = 0;
    	totalInst = 0;
    	reuseFlag = 1;
    }

    //If a model acts the same way as another model similarityMargin proportion of the time, 
    //keep the model with higher accuracy. The kept model gets fade points
	private ArrayList<Integer> mergeModels(ArrayList<Integer> currentModels){
		int modelToRemove;
		int modelToKeep;
		ArrayList<Integer> removedModels = new ArrayList<Integer>();
		for(int i = 0; i < currentModels.size(); i++){
			int modelA = currentModels.get(i);
			if(classifierCollection.get(modelA) == null) continue;
			for(int j = i + 1; j < currentModels.size(); j++){
				int modelB = currentModels.get(j);
				if(classifierCollection.get(modelB) == null) continue;
				if((double)(modelComparisonMeasurements.get(modelA).get(modelB)[1])/(double)(modelComparisonMeasurements.get(modelA).get(modelB)[0]) >= similarityMargin){
					modelMerges++;
					if((double)modelAccuracyMeasurements.get(modelA)[1]/(double)modelAccuracyMeasurements.get(modelA)[0] >=
						(double)modelAccuracyMeasurements.get(modelB)[1]/(double)modelAccuracyMeasurements.get(modelB)[0]){
						modelToRemove = modelB;
						modelToKeep = modelA;
					} else {
						modelToRemove = modelA;
						modelToKeep = modelB;
					}
					removeModel(modelToRemove);
					removedModels.add(modelToRemove);
					if(fadeModels){
						modelFadeScores.put(modelToKeep, modelFadeScores.get(modelToKeep) + ((modelFadeScores.get(modelToRemove) == null) ? 0 : modelFadeScores.get(modelToRemove)));
						modelFadeScores.put(modelToRemove, null);
					}
					if(modelToRemove == currentModels.get(i)){ 
						i++;
						break;
					}
				}
			}
		}
		return removedModels;
	}
	
	private void removeModel(int modelToRemove){
		this.currentModels = this.currentModels - 1;
		classifierCollection.set(modelToRemove, null);
		modelComparisonMeasurements.set(modelToRemove,null);
		modelAccuracyMeasurements.set(modelToRemove,null);
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
		if(this.driftDetectionMethod.getChange()) 
			return true;
		return false;
	}
	
	public void clearBuffer(){
		this.buffer = null;
	}
	
}
