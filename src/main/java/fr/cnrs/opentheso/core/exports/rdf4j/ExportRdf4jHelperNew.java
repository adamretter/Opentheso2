package fr.cnrs.opentheso.core.exports.rdf4j;

import com.zaxxer.hikari.HikariDataSource;
import java.util.ArrayList;

import fr.cnrs.opentheso.bdd.datas.Thesaurus;
import fr.cnrs.opentheso.bdd.helper.ConceptHelper;
import fr.cnrs.opentheso.bdd.helper.GroupHelper;
import fr.cnrs.opentheso.bdd.helper.RelationsHelper;
import fr.cnrs.opentheso.bdd.helper.ThesaurusHelper;
import fr.cnrs.opentheso.bdd.helper.nodes.NodeAlignmentSmall;
import fr.cnrs.opentheso.bdd.helper.nodes.NodeEM;
import fr.cnrs.opentheso.bdd.helper.nodes.NodeGps;
import fr.cnrs.opentheso.bdd.helper.nodes.NodeHieraRelation;
import fr.cnrs.opentheso.bdd.helper.nodes.NodeLangTheso;
import fr.cnrs.opentheso.bdd.helper.nodes.NodePreference;
import fr.cnrs.opentheso.bdd.helper.nodes.NodeUri;
import fr.cnrs.opentheso.bdd.helper.nodes.concept.NodeConceptExport;
import fr.cnrs.opentheso.bdd.helper.nodes.group.NodeGroup;
import fr.cnrs.opentheso.bdd.helper.nodes.group.NodeGroupLabel;
import fr.cnrs.opentheso.bdd.helper.nodes.group.NodeGroupTraductions;
import fr.cnrs.opentheso.bdd.helper.nodes.notes.NodeNote;
import fr.cnrs.opentheso.bdd.helper.nodes.term.NodeTermTraduction;
import fr.cnrs.opentheso.bdd.helper.nodes.thesaurus.NodeThesaurus;
import fr.cnrs.opentheso.bean.importexport.ExportFileBean;
import fr.cnrs.opentheso.skosapi.SKOSGPSCoordinates;
import fr.cnrs.opentheso.skosapi.SKOSProperty;
import fr.cnrs.opentheso.skosapi.SKOSResource;
import fr.cnrs.opentheso.skosapi.SKOSXmlDocument;
import java.util.HashMap;
import java.util.List;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;

/**
 *
 * @author MiledRousset
 */
public class ExportRdf4jHelperNew {

    private String formatDate;

    private boolean useUriArk;
    private boolean useUriHandle;

    private NodePreference nodePreference;
    private SKOSXmlDocument skosXmlDocument;

    private ArrayList<NodeUri> nodeTTs;
   
    private String messages;
    
    // pour gérer les group/sousGroups
    private HashMap<String, String> superGroupHashMap;    

    public ExportRdf4jHelperNew() {
        skosXmlDocument = new SKOSXmlDocument();
        superGroupHashMap = new HashMap();
    }

    public boolean setInfos(
            NodePreference nodePreference,
            String formatDate, boolean useUriArk, boolean useUriHandle) {
        this.formatDate = formatDate;
        this.useUriArk = useUriArk;
        this.useUriHandle = useUriHandle;
        this.nodePreference = nodePreference;
        messages = "";
        return true;
    }

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    //////////////////////// Nouvelles fonctions ///////////////////////////////    
    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////    
    ///// étapes pour exporter un thésaurus sans limitation de langues ni de collections :
    ///// - export des infos sur le thésaurus
    ///// - ajout des balises (hasTopConcept) au thésaurus thésaurus
    ///// - export des collections et les membres
    ///// - export des concepts 
    /**
     * permet de récupérer les informations du thésaurus et les TopConcept pour
     * construire SKOSResource #MR
     *
     * @param ds
     * @param idTheso
     * @return 
     */
    public void exportTheso(HikariDataSource ds, String idTheso) {
        NodeThesaurus nodeThesaurus = new ThesaurusHelper().getNodeThesaurus(ds, idTheso);
        String uri = getUriFromId(nodeThesaurus.getIdThesaurus());
        SKOSResource conceptScheme = new SKOSResource(uri, SKOSProperty.ConceptScheme);
//        idTheso = nodeThesaurus.getIdThesaurus();
        String creator;
        String contributor;
        String title;
        String language;

        for (Thesaurus thesaurus : nodeThesaurus.getListThesaurusTraduction()) {
            creator = thesaurus.getCreator();
            contributor = thesaurus.getContributor();
            title = thesaurus.getTitle();
            language = thesaurus.getLanguage();

            if (creator != null && !creator.equalsIgnoreCase("null")) {
                conceptScheme.addCreator(creator, SKOSProperty.creator);
            }
            if (contributor != null && !contributor.equalsIgnoreCase("null")) {
                conceptScheme.addCreator(creator, SKOSProperty.contributor);
            }
            if (title != null && language != null) {
                conceptScheme.addLabel(title, language, SKOSProperty.prefLabel);
            }

            //dates
            String created = thesaurus.getCreated().toString();
            String modified = thesaurus.getModified().toString();
            if (created != null) {
                conceptScheme.addDate(created, SKOSProperty.created);
            }
            if (modified != null) {
                conceptScheme.addDate(modified, SKOSProperty.modified);
            }
        }

        //liste top concept
        ConceptHelper conceptHelper = new ConceptHelper();
        nodeTTs = conceptHelper.getAllTopConcepts(
                ds,
                idTheso);

        nodeTTs.forEach((nodeTT) -> {
            conceptScheme.addRelation(getUriFromNodeUri(nodeTT, idTheso), SKOSProperty.hasTopConcept);
        });
        skosXmlDocument.setConceptScheme(conceptScheme);
    }
    
    public void exportCollections(HikariDataSource ds, String idTheso, ExportFileBean exportFileBean){
        GroupHelper groupHelper = new GroupHelper();
        ArrayList<String> rootGroupList = groupHelper.getListIdOfRootGroup(ds, idTheso);
        NodeGroupLabel nodeGroupLabel;
        for (String idGroup : rootGroupList) {
            exportFileBean.setProgressBar(exportFileBean.getProgressBar() + exportFileBean.getProgressStep());
            
            nodeGroupLabel = groupHelper.getNodeGroupLabel(ds, idGroup, idTheso);
            SKOSResource sKOSResource = new SKOSResource(getUriFromGroup(nodeGroupLabel), SKOSProperty.ConceptGroup);                    
            sKOSResource.addRelation(getUriFromGroup(nodeGroupLabel), SKOSProperty.microThesaurusOf);
            addChildsGroupRecursive(ds, idTheso, idGroup, sKOSResource);                    
        }
    }
    private void addChildsGroupRecursive(HikariDataSource ds,
            String idTheso,
            String idParent,
            SKOSResource sKOSResource) {
        GroupHelper groupHelper = new GroupHelper();

        ArrayList<String> listIdsOfGroupChilds = groupHelper.getListGroupChildIdOfGroup(ds, idParent, idTheso);
        writeGroupInfo(ds, sKOSResource, idTheso, idParent);

        for (String idOfGroupChild : listIdsOfGroupChilds) {
            sKOSResource = new SKOSResource();
            addChildsGroupRecursive(ds, idTheso, idOfGroupChild, sKOSResource);
        }
    }

    private void writeGroupInfo(HikariDataSource ds, SKOSResource sKOSResource,
            String idTheso, String idOfGroupChild) {

        NodeGroupLabel nodeGroupLabel;
        nodeGroupLabel = new GroupHelper().getNodeGroupLabel(ds, idOfGroupChild, idTheso);

        sKOSResource.setUri(getUriFromGroup(nodeGroupLabel));
        sKOSResource.setProperty(SKOSProperty.ConceptGroup);

        for (NodeGroupTraductions traduction : nodeGroupLabel.getNodeGroupTraductionses()) {
            sKOSResource.addLabel(traduction.getTitle(), traduction.getIdLang(), SKOSProperty.prefLabel);

            //dates
            String created;
            String modified;
            created = traduction.getCreated().toString();
            modified = traduction.getModified().toString();
            if (created != null) {
                sKOSResource.addDate(created, SKOSProperty.created);
            }
            if (modified != null) {
                sKOSResource.addDate(modified, SKOSProperty.modified);
            }
        }

        ArrayList<String> childURI = new GroupHelper().getListGroupChildIdOfGroup(ds, idOfGroupChild, idTheso);
//        ArrayList<NodeUri> nodeUris = new ConceptHelper().getListIdsOfTopConceptsForExport(ds, idOfGroupChild, idTheso);
        
        ArrayList<NodeUri> nodeUris = new ConceptHelper().getListConceptsOfGroup(ds, idTheso, idOfGroupChild);
        
        for (NodeUri nodeUri : nodeUris) {
            sKOSResource.addRelation(getUriFromNodeUri(nodeUri, idTheso), SKOSProperty.member);
      //      addMember(ds, nodeUri.getIdConcept(), idTheso, sKOSResource);
        }

        for (String id : childURI) {
            sKOSResource.addRelation(getUriFromId(id), SKOSProperty.subGroup);
            superGroupHashMap.put(id, idOfGroupChild);
        }

        String idSuperGroup = superGroupHashMap.get(idOfGroupChild);

        if (idSuperGroup != null) {
            sKOSResource.addRelation(getUriFromId(idSuperGroup), SKOSProperty.superGroup);
            superGroupHashMap.remove(idOfGroupChild);
        }
        
        // ajout de la notation
        if (nodeGroupLabel.getNotation() != null && !nodeGroupLabel.getNotation().equals("null")) {
            if(!nodeGroupLabel.getNotation().isEmpty())
                sKOSResource.addNotation(nodeGroupLabel.getNotation());
        }
        skosXmlDocument.addGroup(sKOSResource);
    } 
    private void addMember(HikariDataSource ds,
            String id, String idThesaurus, SKOSResource resource) {

        RelationsHelper relationsHelper = new RelationsHelper();
        ArrayList<NodeHieraRelation> listChildren = relationsHelper.getListNT(ds, id, idThesaurus);

        for (NodeHieraRelation idChildren : listChildren) {
            resource.addRelation(getUriFromNodeUri(idChildren.getUri(), idThesaurus), SKOSProperty.member);
            addMember(ds, idChildren.getUri().getIdConcept(), idThesaurus, resource);
        }
    }    
    

    /**
     * permet d'ajouter un concept à l'export en cours
     *
     * @param ds
     * @param idTheso
     * @param idConcept
     */
    public void exportConcept(HikariDataSource ds, String idTheso, String idConcept) {
        SKOSResource sKOSResource = new SKOSResource();

        ConceptHelper conceptHelper = new ConceptHelper();
        NodeConceptExport nodeConcept = conceptHelper.getConceptForExport(ds, idConcept, idTheso, false);

        
        if (nodeConcept == null) {
            messages = messages + ("Erreur concept non exporté importé: " + idConcept + "\n");
            return;
        }
        
        if(nodeConcept.getNodeListOfBT().isEmpty() ){
            sKOSResource.addRelation(getUriFromId(idTheso), SKOSProperty.topConceptOf);
        }
        
        //    concept.setUri(getUriFromId(idConcept));
        sKOSResource.setUri(getUri(nodeConcept));
        sKOSResource.setProperty(SKOSProperty.Concept);

        // prefLabel
        for (NodeTermTraduction traduction : nodeConcept.getNodeTermTraductions()) {
            sKOSResource.addLabel(traduction.getLexicalValue(), traduction.getLang(), SKOSProperty.prefLabel);
        }
        // altLabel
        for (NodeEM nodeEM : nodeConcept.getNodeEM()) {
            if (nodeEM.isHiden()) {
                sKOSResource.addLabel(nodeEM.getLexical_value(), nodeEM.getLang(), SKOSProperty.hiddenLabel);
            } else {
                sKOSResource.addLabel(nodeEM.getLexical_value(), nodeEM.getLang(), SKOSProperty.altLabel);
            }
        }
        ArrayList<NodeNote> nodeNotes = nodeConcept.getNodeNoteConcept();
        nodeNotes.addAll(nodeConcept.getNodeNoteTerm());
        nodeNotes.addAll(nodeConcept.getNodeNoteConcept());        
        addNoteGiven(nodeNotes, sKOSResource);
        addGPSGiven(nodeConcept.getNodeGps(), sKOSResource);
        addAlignementGiven(nodeConcept.getNodeAlignmentsList(), sKOSResource);
        addRelationGiven(nodeConcept.getNodeListOfBT(), nodeConcept.getNodeListOfNT(),
                nodeConcept.getNodeListIdsOfRT(), sKOSResource, nodeConcept.getConcept().getIdThesaurus());


        if (nodeConcept.getConcept().getNotation() != null && !nodeConcept.getConcept().getNotation().equals("null")) {
            sKOSResource.addNotation(nodeConcept.getConcept().getNotation());
        }
        if (nodeConcept.getConcept().getCreated().toString() != null) {
            sKOSResource.addDate(nodeConcept.getConcept().getCreated().toString(), SKOSProperty.created);
        }
        if (nodeConcept.getConcept().getModified().toString() != null) {
            sKOSResource.addDate(nodeConcept.getConcept().getModified().toString(), SKOSProperty.modified);
        }
        sKOSResource.addRelation(getUriFromId(idTheso), SKOSProperty.inScheme);
        for (NodeUri nodeUri : nodeConcept.getNodeListIdsOfConceptGroup()) {
            sKOSResource.addRelation(getUriGroupFromNodeUri(nodeUri, idTheso), SKOSProperty.memberOf);
        }
        sKOSResource.addIdentifier(idConcept, SKOSProperty.identifier);

        ArrayList<String> first = new ArrayList<>();
        first.add(idConcept);
        ArrayList<ArrayList<String>> paths = new ArrayList<>();

        paths = new ConceptHelper().getPathOfConceptWithoutGroup(ds, idConcept, idTheso, first, paths);
        ArrayList<String> pathFromArray = getPathFromArray(paths);
        if (!pathFromArray.isEmpty()) {
            sKOSResource.setPaths(pathFromArray);
        }
        //    sKOSResource.setPath("A/B/C/D/"+idConcept);
        
        // les images
        if(nodeConcept.getNodeimages() != null || (!nodeConcept.getNodeimages().isEmpty())) {
            for (String imageUri : nodeConcept.getNodeimages()) {
                sKOSResource.addImageUri(imageUri);
            }
        }
        
        skosXmlDocument.addconcept(sKOSResource);
    }

    private ArrayList<String> getPathFromArray(ArrayList<ArrayList<String>> paths) {
        String pathFromArray = "";
        ArrayList<String> allPath = new ArrayList<>();
        for (ArrayList<String> path : paths) {
            for (String string1 : path) {
                if(pathFromArray.isEmpty())
                    pathFromArray = string1;
                else
                    pathFromArray = pathFromArray + "##" + string1;
            }
            allPath.add(pathFromArray);
            pathFromArray = "";
        }
        return allPath;
    }
    
    private void addNoteGiven(ArrayList<NodeNote> nodeNotes, SKOSResource resource) {
        for (NodeNote note : nodeNotes) {
            int prop;
            switch (note.getNotetypecode()) {
                case "note":
                    prop = SKOSProperty.note;
                    break;                  
                case "scopeNote":
                    prop = SKOSProperty.scopeNote;
                    break;
                case "historyNote":
                    prop = SKOSProperty.historyNote;
                    break;
                case "example":
                    prop = SKOSProperty.example;
                    break;                    
                case "definition":
                    prop = SKOSProperty.definition;
                    break;
                case "editorialNote":
                    prop = SKOSProperty.editorialNote;
                    break;
                case "changeNote":
                    prop = SKOSProperty.changeNote;
                    break;             
                default:
                    prop = SKOSProperty.note;
                    break;
            }
            resource.addDocumentation(note.getLexicalvalue(), note.getLang(), prop);
        }
    }
    private void addGPSGiven(NodeGps gps, SKOSResource resource) {
        if (gps == null) {
            return;
        }
        double lat = gps.getLatitude();
        double lon = gps.getLongitude();
        resource.setGPSCoordinates(new SKOSGPSCoordinates(lat, lon));
    }  
    private void addAlignementGiven(ArrayList<NodeAlignmentSmall> nodeAlignments, SKOSResource resource) {
        for (NodeAlignmentSmall alignment : nodeAlignments) {

            int prop = -1;
            switch (alignment.getAlignement_id_type()) {

                case 1:
                    prop = SKOSProperty.exactMatch;
                    break;
                case 2:
                    prop = SKOSProperty.closeMatch;
                    break;
                case 3:
                    prop = SKOSProperty.broadMatch;
                    break;
                case 4:
                    prop = SKOSProperty.relatedMatch;
                    break;
                case 5:
                    prop = SKOSProperty.narrowMatch;
                    break;
            }
            resource.addMatch(alignment.getUri_target(), prop);
        }
    }
    private void addRelationGiven(ArrayList<NodeHieraRelation> btList, ArrayList<NodeHieraRelation> ntList,
            ArrayList<NodeHieraRelation> rtList, SKOSResource resource, String idTheso) {
        for (NodeHieraRelation rt : rtList) {
            int prop;

            switch (rt.getRole()) {
                case "RHP":
                    prop = SKOSProperty.relatedHasPart;
                    break;
                case "RPO":
                    prop = SKOSProperty.relatedPartOf;
                    break;
                default:
                    prop = SKOSProperty.related;
            }
            resource.addRelation(getUriFromNodeUri(rt.getUri(), idTheso), prop);
        }
        for (NodeHieraRelation nt : ntList) {
            int prop;
            switch (nt.getRole()) {
                case "NTG":
                    prop = SKOSProperty.narrowerGeneric;
                    break;
                case "NTP":
                    prop = SKOSProperty.narrowerPartitive;
                    break;
                case "NTI":
                    prop = SKOSProperty.narrowerInstantial;
                    break;
                default:
                    prop = SKOSProperty.narrower;
            }
            resource.addRelation(getUriFromNodeUri(nt.getUri(), idTheso), prop);
        }
        for (NodeHieraRelation bt : btList) {

            int prop;
            switch (bt.getRole()) {
                case "BTG":
                    prop = SKOSProperty.broaderGeneric;
                    break;
                case "BTP":
                    prop = SKOSProperty.broaderPartitive;
                    break;
                case "BTI":
                    prop = SKOSProperty.broaderInstantial;
                    break;
                default:
                    prop = SKOSProperty.broader;
            }
            resource.addRelation(getUriFromNodeUri(bt.getUri(), idTheso), prop);
        }
    }    

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    /////////////////////Fin des nouvelles fonctions ///////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////// 
    public String getUriFromId(String id) {
        String uri;
        uri = nodePreference.getCheminSite() + id;
        return uri;
    }

    /**
     * Cette fonction permet de retourner l'URI du concept avec identifiant Ark
     * : si renseigné sinon l'URL du Site
     *
     * @param nodeConceptExport
     * @return
     */
    private String getUri(NodeConceptExport nodeConceptExport) {
        String uri = "";
        if (nodeConceptExport == null) {
            //      System.out.println("nodeConcept = Null");
            return uri;
        }
        if (nodeConceptExport.getConcept() == null) {
            //    System.out.println("nodeConcept.getConcept = Null");
            return uri;
        }

        // Choix de l'URI pour l'export : 
        // Si Handle est actif, on le prend en premier 
        // sinon,  on vérifie si Ark est actif, 
        // en dernier, on prend l'URL basique d'Opentheso
        // 1 seule URI est possible pour l'export par concept
        // URI de type Ark
        if(nodePreference.isOriginalUriIsArk()) {
            if (nodeConceptExport.getConcept().getIdArk() != null) {
                if (!nodeConceptExport.getConcept().getIdArk().trim().isEmpty()) {
                    uri = nodePreference.getUriArk()+ nodeConceptExport.getConcept().getIdArk();
                    return uri;
                }
            }
        }
        
        if(nodePreference.isOriginalUriIsHandle()) {
            // URI de type Handle
            if (nodeConceptExport.getConcept().getIdHandle() != null) {
                if (!nodeConceptExport.getConcept().getIdHandle().trim().isEmpty()) {
                    uri = "https://hdl.handle.net/" + nodeConceptExport.getConcept().getIdHandle();
                    return uri;
                }
            }
        }
        // si on ne trouve pas ni Handle, ni Ark
        //    uri = nodePreference.getCheminSite() + nodeConceptExport.getConcept().getIdConcept();
        uri = nodePreference.getCheminSite() + "?idc=" + nodeConceptExport.getConcept().getIdConcept()
                        + "&idt=" + nodeConceptExport.getConcept().getIdThesaurus();
//        uri = nodePreference.getCheminSite() + nodeConceptExport.getConcept().getIdConcept();

        return uri;
    }

    /**
     * Cette fonction permet de retourner l'URI du concept avec identifiant Ark
     * : si renseigné sinon l'URL du Site
     *
     * @param nodeConceptExport
     * @return
     */
    private String getUriFromGroup(NodeGroupLabel nodeGroupLabel) {
        String uri = "";
        if (nodeGroupLabel == null) {
            //      System.out.println("nodeConcept = Null");
            return uri;
        }
        if (nodeGroupLabel.getIdGroup() == null) {
            //    System.out.println("nodeConcept.getConcept = Null");
            return uri;
        }

        // Choix de l'URI pour l'export : 
        // Si Handle est actif, on le prend en premier 
        // sinon,  on vérifie si Ark est actif, 
        // en dernier, on prend l'URL basique d'Opentheso
        // 1 seule URI est possible pour l'export par concept
        // URI de type Ark
        if(nodePreference.isOriginalUriIsArk()) {
            if (nodeGroupLabel.getIdArk() != null) {
                if (!nodeGroupLabel.getIdArk().trim().isEmpty()) {
                    uri = nodePreference.getUriArk() + nodeGroupLabel.getIdArk();
                    return uri;
                }
            }
        }
        if(nodePreference.isOriginalUriIsHandle()) {        
            // URI de type Handle
            if (nodeGroupLabel.getIdHandle() != null) {
                if (!nodeGroupLabel.getIdHandle().trim().isEmpty()) {
                    uri = "https://hdl.handle.net/" + nodeGroupLabel.getIdHandle();
                    return uri;
                }
            }
        }
        // si on ne trouve pas ni Handle, ni Ark
//        uri = nodePreference.getCheminSite() + nodeGroupLabel.getIdGroup();
        uri = nodePreference.getCheminSite() + "?idg=" + nodeGroupLabel.getIdGroup()
                    + "&idt=" + nodeGroupLabel.getIdThesaurus();

    //    uri = nodePreference.getCheminSite() + nodeGroupLabel.getIdGroup();
        return uri;
    }

    /**
     * Cette fonction permet de retourner l'URI du concept avec identifiant Ark
     * : si renseigné sinon l'URL du Site
     *
     * @param nodeConceptExport
     * @return
     */
    private String getUriGroupFromNodeUri(NodeUri nodeUri, String idTheso) {
        String uri = "";
        if (nodeUri == null) {
            //      System.out.println("nodeConcept = Null");
            return uri;
        }

        // Choix de l'URI pour l'export : 
        // Si Handle est actif, on le prend en premier 
        // sinon,  on vérifie si Ark est actif, 
        // en dernier, on prend l'URL basique d'Opentheso
        // 1 seule URI est possible pour l'export par concept
        // URI de type Ark
        if (nodeUri.getIdArk() != null) {
            if (!nodeUri.getIdArk().trim().isEmpty()) {
                uri = nodePreference.getServeurArk() + nodeUri.getIdArk();
                return uri;
            }
        }
        // URI de type Handle
        if (nodeUri.getIdHandle() != null) {
            if (!nodeUri.getIdHandle().trim().isEmpty()) {
                uri = "https://hdl.handle.net/" + nodeUri.getIdHandle();
                return uri;
            }
        }

        // si on ne trouve pas ni Handle, ni Ark
        //    uri = nodePreference.getCheminSite() + nodeUri.getIdConcept();
//        uri = nodePreference.getCheminSite() + "?idg=" + nodeUri.getIdConcept()
//                        + "&idt=" + idTheso;
        uri = nodePreference.getCheminSite() + nodeUri.getIdConcept();

        return uri;
    }

    /**
     * Cette fonction permet de retourner l'URI du concept avec identifiant Ark
     * : si renseigné sinon l'URL du Site
     *
     * @param nodeConceptExport
     * @return
     */
    private String getUriFromNodeUri(NodeUri nodeUri, String idTheso) {
        String uri = "";
        if (nodeUri == null) {
            //      System.out.println("nodeConcept = Null");
            return uri;
        }

        // Choix de l'URI pour l'export : 
        // Si Handle est actif, on le prend en premier 
        // sinon,  on vérifie si Ark est actif, 
        // en dernier, on prend l'URL basique d'Opentheso
        // 1 seule URI est possible pour l'export par concept
        // URI de type Ark

        if(nodePreference.isOriginalUriIsArk()) {
            if (nodeUri.getIdArk() != null) {
                if (!nodeUri.getIdArk().trim().isEmpty()) {
                    uri = nodePreference.getUriArk() + nodeUri.getIdArk();
                    return uri;
                }
            }
        }
        if(nodePreference.isOriginalUriIsHandle()) {
            // URI de type Handle
            if (nodeUri.getIdHandle() != null) {
                if (!nodeUri.getIdHandle().trim().isEmpty()) {
                    uri = "https://hdl.handle.net/" + nodeUri.getIdHandle();
                    return uri;
                }
            }
        }

        // si on ne trouve pas ni Handle, ni Ark
        uri = nodePreference.getCheminSite() + "?idc=" + nodeUri.getIdConcept()
                        + "&idt=" + idTheso;   
                        //+ "&amp;idt=" + idTheso;
    //    uri = nodePreference.getCheminSite() + nodeUri.getIdConcept();
        return uri;
    }

    public SKOSXmlDocument getSkosXmlDocument() {
        return skosXmlDocument;
    }

    public void getMessages() {
        if(messages != null) {
            if (!messages.isEmpty()) {
                FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "Info :", messages));
            }
        }
    }
  
}
