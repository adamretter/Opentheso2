/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.cnrs.opentheso.bean.toolbox.edition;

import fr.cnrs.opentheso.bdd.helper.ConceptHelper;
import fr.cnrs.opentheso.bdd.helper.PreferencesHelper;
import fr.cnrs.opentheso.bdd.helper.ThesaurusHelper;
import fr.cnrs.opentheso.bdd.helper.UserHelper;
import fr.cnrs.opentheso.bdd.helper.nodes.NodeIdValue;
import fr.cnrs.opentheso.bdd.helper.nodes.NodePreference;
import fr.cnrs.opentheso.bean.menu.connect.Connect;
import javax.inject.Named;
import javax.enterprise.context.SessionScoped;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.inject.Inject;

/**
 *
 * @author miledrousset
 */
@Named(value = "deleteThesoBean")
@SessionScoped
public class DeleteThesoBean implements Serializable {
    @Inject private Connect connect;
    
    private String idThesoToDelete;
    private String valueOfThesoToDelelete;
    private boolean isDeleteOn;
    /**
     * Creates a new instance of DeleteThesoBean
     */
    public DeleteThesoBean() {
        idThesoToDelete = null;
        valueOfThesoToDelelete = null;
        isDeleteOn = false;
    }
    
    public void init() {
        idThesoToDelete = null;
        valueOfThesoToDelelete = null;
        isDeleteOn = false;        
    }
    
    public void confirmDelete(NodeIdValue nodeTheso) {
        this.idThesoToDelete = nodeTheso.getId();
        this.valueOfThesoToDelelete = nodeTheso.getValue();
        isDeleteOn = true;
    }
    
    /**
     * Permet de supprimer un thésaurus 
     */
    public void deleteTheso() {
        if(idThesoToDelete == null) return;
        PreferencesHelper preferencesHelper = new PreferencesHelper();
        NodePreference nodePreference = preferencesHelper.getThesaurusPreferences(connect.getPoolConnexion(), idThesoToDelete);
        if(nodePreference != null) {
            // suppression des Identifiants Handle
            ConceptHelper conceptHelper = new ConceptHelper();
            conceptHelper.setNodePreference(nodePreference);
            conceptHelper.deleteAllIdHandle(connect.getPoolConnexion(), idThesoToDelete);
        }
        FacesMessage msg;
        
        // supression des droits
        UserHelper userHelper = new UserHelper();
        try {
            Connection conn = connect.getPoolConnexion().getConnection();
            conn.setAutoCommit(false);
            if(!userHelper.deleteThesoFromGroup(conn, idThesoToDelete)) {
                msg = new FacesMessage(FacesMessage.SEVERITY_ERROR, "Erreur", "Erreur pendant la suppression !!!");
                FacesContext.getCurrentInstance().addMessage(null, msg);
                conn.rollback();
                conn.commit();
                return;
            }
            conn.commit();
            conn.close();
        } catch (SQLException ex) {
            Logger.getLogger(DeleteThesoBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        // suppression complète du thésaurus        
        ThesaurusHelper thesaurusHelper = new ThesaurusHelper();
        if(!thesaurusHelper.deleteThesaurus(connect.getPoolConnexion(), idThesoToDelete)){
            msg = new FacesMessage(FacesMessage.SEVERITY_ERROR, "Erreur", "Erreur pendant la suppression !!!");
            FacesContext.getCurrentInstance().addMessage(null, msg);
            return;
        }
        msg = new FacesMessage(FacesMessage.SEVERITY_INFO, "info", "thesaurus supprimé avec succès");
        FacesContext.getCurrentInstance().addMessage(null, msg);
        init();
    }

    public boolean isIsDeleteOn() {
        return isDeleteOn;
    }

    public void setIsDeleteOn(boolean isDeleteOn) {
        this.isDeleteOn = isDeleteOn;
    }

    public String getIdThesoToDelete() {
        return idThesoToDelete;
    }

    public void setIdThesoToDelete(String idThesoToDelete) {
        this.idThesoToDelete = idThesoToDelete;
    }

    public String getValueOfThesoToDelelete() {
        return valueOfThesoToDelelete;
    }

    public void setValueOfThesoToDelelete(String valueOfThesoToDelelete) {
        this.valueOfThesoToDelelete = valueOfThesoToDelelete;
    }
    
    
    
}
