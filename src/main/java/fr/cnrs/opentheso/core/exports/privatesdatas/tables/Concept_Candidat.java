/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.cnrs.opentheso.core.exports.privatesdatas.tables;

import java.util.ArrayList;
import java.util.Date;
import fr.cnrs.opentheso.core.exports.privatesdatas.LineOfData;

/**
 *
 * @author antonio.perez
 */
public class Concept_Candidat {
    private ArrayList<LineOfData> lineOfDatas;

    public ArrayList<LineOfData> getLineOfDatas() {
        return lineOfDatas;
    }

    public void setLineOfDatas(ArrayList<LineOfData> lineOfDatas) {
        this.lineOfDatas = lineOfDatas;
    }
    
}