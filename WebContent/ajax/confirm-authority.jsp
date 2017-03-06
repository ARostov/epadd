<%@page language="java" contentType="application/json;charset=UTF-8"%>
<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page import="edu.stanford.muse.ie.AuthorityMapper"%>
<%
// does a login for a particular account, and adds the emailStore to the session var emailStores (list of stores for the current doLogin's)
JSPHelper.setPageUncacheable(response);
String fastIdStr = request.getParameter ("fastId");
JSONObject result = new JSONObject();

String name = request.getParameter ("name");
Archive archive = JSPHelper.getArchive(session);
AuthorityMapper am = archive.getAuthorityMapper();

if (request.getParameter ("unset") == null) {
    long fastId = 0;
    try { fastId = Long.parseLong (fastIdStr); }
    catch (Exception nfe) {
        result.put ("status", 1);
        result.put ("error", "Invalid long for fast id: " + fastIdStr);
    }
    am.setAuthRecord (name, fastId, request.getParameter ("viafId"), request.getParameter ("wikipediaId"), request.getParameter ("lcnafId"), request.getParameter ("lcshId"), request.getParameter ("localId"), request.getParameter("isManualAssign") != null);
} else {
    am.unsetAuthRecord (name);
}

out.println (result.toString());
%>
