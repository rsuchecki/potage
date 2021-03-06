/* 
 * Copyright 2016 University of Adelaide.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package poplogic;

import java.io.Serializable;
import java.util.ArrayList;

/**
 *
 * @author Radoslaw Suchecki <radoslaw.suchecki@adelaide.edu.au>
 */
public class Contig implements Serializable { //, Comparable<Contig> {
    //wheat contig info

    private final String contigId;
//    private final String popedAtChromosome;
    private final Double cM;
    private final String chromosome;
    

//    private long len;
    private ArrayList<String> wheatGeneIdsList;
//    private ArrayList<Annotation> annotations;
//    private ArrayList<Annotation> annotationsRice;
//    private HashMap<String, ArrayList<Double>> genesTissuesFPKMsMap;

    public Contig(String contigId, String chromosome, double cM, ArrayList<String> wheatGeneIdsList) {
        this.contigId = contigId;
//        this.popedAtChromosome = popedAtChromosome;
        this.chromosome = chromosome;
        this.cM = cM;

        this.wheatGeneIdsList = wheatGeneIdsList;
//        this.genesTissuesFPKMsMap = genesTissuesFPKMsMap;
    }

    public Contig(String contigId, String chromosome, double cM) {
        this.contigId = contigId;
        this.chromosome = chromosome;
        this.cM = cM;
    }
    
    public Contig(String contigId, double cM) {
        this.contigId = contigId;
        this.chromosome =  contigId.split("_")[0].substring(0, 2);
        this.cM = cM;
    }

    public Contig(String contigId) {
        this.contigId = contigId;
//        this.popedAtChromosome = null;
        this.cM = null;
        this.wheatGeneIdsList = null;
        this.chromosome = contigId.split("_")[0].substring(0, 2);
    }
//    public Contig(String contigId, String popedAtChromosome, double cM_corrected, double cM_original, ArrayList<String> wheatGeneIdsList, 
//            ArrayList<Annotation> annotations, ArrayList<Annotation> annotationsRice, HashMap<String, ArrayList<Double>> genesTissuesFPKMsMap) {
//        this.contigId = contigId;
//        this.popedAtChromosome = popedAtChromosome;
//        this.cM_corrected = cM_corrected;
//        this.cM_original = cM_original;
//        this.annotations = annotations;
//        this.annotationsRice = annotationsRice;
//        this.wheatGeneIdsList = wheatGeneIdsList;
////        this.genesTissuesFPKMsMap = genesTissuesFPKMsMap;
//    }

    public String getContigId() {
        return contigId;
    }

//    public String getPopedAtChromosome() {
//        return popedAtChromosome;
//    }
    public Double getcM() {
        return cM;
    }

    public ArrayList<String> getWheatGeneIdsList() {
        return wheatGeneIdsList;
    }

    public void setWheatGeneIdsList(ArrayList<String> wheatGeneIdsList) {
        this.wheatGeneIdsList = wheatGeneIdsList;
    }
    
    

    public boolean hasGenes() {
        if (wheatGeneIdsList == null || wheatGeneIdsList.isEmpty()) {
            return false;
        }
        return true;
    }

//    public void setAnnotations(ArrayList<Annotation> annotations) {
//        this.annotations = annotations;
//    }
//
//    public ArrayList<Annotation> getAnnotations() {
//        return annotations;
//    }
//
//    public ArrayList<Annotation> getAnnotationsRice() {
//        return annotationsRice;
//    }
//
//    public void setAnnotationsRice(ArrayList<Annotation> annotationsRice) {
//        this.annotationsRice = annotationsRice;
//    }
//    @Override
//    public int compareTo(Contig another ) {        
//        return (int) (getFrom()-another.getFrom());        
//    }    
    public String getId() {
        return contigId;
    }

    public String getChromosome() {
        return chromosome;
    }
    
    

    private String getColour(double value) {
        if (value >= 90) {
            return "color: green";
        } else if (value >= 75) {
            return "color: #FF7E00";
        } else {
            return "color: red";
        }
    }
}
