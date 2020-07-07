package fr.cnrs.opentheso.bean.condidat.enumeration;

public enum TermEnum {

    TERME_GENERIQUE("BT"),
    TERME_ASSOCIE("NT"),
    EMPLOYE("RT");

    private String label;

    TermEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
