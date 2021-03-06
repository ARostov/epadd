<%@page contentType="text/html; charset=UTF-8"%>
<%@page trimDirectiveWhitespaces="true"%>
<%
    JSPHelper.checkContainer(request); // do this early on so we are set up
    request.setCharacterEncoding("UTF-8");
%>
<%@page language="java" import="edu.stanford.muse.datacache.Blob"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.DetailedFacetItem"%>
<%@page language="java" import="edu.stanford.muse.util.EmailUtils"%>
<%@page language="java" import="edu.stanford.muse.util.Pair"%>
<%@page language="java" import="edu.stanford.muse.webapp.EmailRenderer"%>
<%@page language="java" import="edu.stanford.muse.webapp.HTMLUtils"%>
<%@page language="java" import="edu.stanford.muse.webapp.NewFilter"%>
<%@ page import="java.util.*" %>
<%@ page import="java.util.stream.Stream" %>
<%@ page import="org.apache.poi.hdgf.streams.StringsStream" %>
<%@ page import="com.google.common.collect.Multimap" %>

<%@include file="getArchive.jspf" %>

<%
    String term = JSPHelper.convertRequestParamToUTF8(request.getParameter("term"));
    String title = request.getParameter("title");
    //<editor-fold desc="Derive title if the original title is not set" input="request" output="title"
    // name="search-title-derivation">
    {
        if (Util.nullOrEmpty(title)) {

            // good to give a meaningful title to the browser tab since a lot of them may be open
            // warning: remember to convert, otherwise will not work for i18n queries!
            String sentiments[] = JSPHelper.convertRequestParamsToUTF8(request.getParameterValues("lexiconCategory"));

            String[] persons = JSPHelper.convertRequestParamsToUTF8(request.getParameterValues("person"));
            String[] attachments = JSPHelper.convertRequestParamsToUTF8(request.getParameterValues("attachment"));

            int month = HTMLUtils.getIntParam(request, "month", -1);
            int year = HTMLUtils.getIntParam(request, "year", -1);
            int cluster = HTMLUtils.getIntParam(request, "timeCluster", -1);

            String sentimentSummary = "";
            if (sentiments != null && sentiments.length > 0)
                for (int i = 0; i < sentiments.length; i++) {
                    sentimentSummary += sentiments[i];
                    if (i < sentiments.length - 1)
                        sentimentSummary += " & ";
                }

            if (term != null)
                title = "Search: " + term;
            else if (cluster != -1)
                title = "Cluster " + cluster;
            else if (!Util.nullOrEmpty(sentimentSummary))
                title = sentimentSummary;
            else if (attachments != null && attachments.length > 0)
                title = attachments[0];
            else if (month >= 0 && year >= 0)
                title = month + "/" + year;
            else if (year >= 0)
                title = Integer.toString(year);
            else if (persons != null && persons.length > 0) {
                title = persons[0];
                if (persons.length > 1)
                    title += "+" + (persons.length - 1);
            } else
                title = "Browse";
            title = Util.escapeHTML(title);
        }
    }
    //</editor-fold>

    //<editor-fold desc="Setup (load archive/prepare session) for public mode- if present" input="request"
    // output="">
    /*if (ModeConfig.isPublicMode()) {
        // this browse page is also used by Public mode where the following set up may be requried.
        String archiveId = request.getParameter("aId");
        Sessions.loadSharedArchiveAndPrepareSession(session, archiveId);
    }*/
    //</editor-fold>

    String datasetName;
    //<editor-fold desc="generate a random id for this dataset (the terms docset and dataset are used interchangeably)"
    // input="" output="String:datasetName">
    {
        datasetName = String.format("docset-%08x", EmailUtils.rng.nextInt());// "dataset-1";
    }
    //</editor-fold>

%>
<!DOCTYPE HTML>
<html lang="en">
<head>
    <META http-equiv="Content-Type" content="text/html; charset=UTF-8">

    <title><%=title%></title>

    <link rel="icon" type="image/png" href="images/epadd-favicon.png">

    <link rel="stylesheet" href="bootstrap/dist/css/bootstrap.min.css">
    <link rel="stylesheet" href="css/jquery.qtip.min.css">
    <jsp:include page="css/css.jsp"/>

    <script src="js/stacktrace.js" type="text/javascript"></script>
    <script src="js/jquery.js" type="text/javascript"></script>

    <script type='text/javascript' src='js/jquery.qtip.min.js'></script>
    <script type="text/javascript" src="bootstrap/dist/js/bootstrap.min.js"></script>

    <script src="js/muse.js" type="text/javascript"></script>
    <script src="js/epadd.js"></script>
    <script>var archiveID = '<%=SimpleSessions.getArchiveIDForArchive(archive)%>';</script> <!-- make the archiveID available to browse.js -->
    <script>var datasetName = '<%=datasetName%>';</script> <!-- make the dataset name available to browse.js -->
    <script src="js/browse.js" type="text/javascript"></script>
    <script type='text/javascript' src='js/utils.js'></script>     <!-- For tool-tips -->

    <style> div.facets hr { width: 90%; } </style>

</head>
<body > <!--  override margin because this page is framed. -->
<jsp:include page="header.jspf"/>
<script>epadd.nav_mark_active('Browse');</script>

<!--  important: include jog_plugin AFTER header.jsp, otherwise the extension is applied to
a jquery ($) object that is overwritten when header.jsp is included! -->
<script src="js/jog_plugin.js" type="text/javascript"></script>

<%

    Collection<Document> docs;
    SearchResult outputSet;
    //<editor-fold desc="Search archive based on request parameters and return the result" input="archive;request"
    // output="collection:docs;collection:attachments(highlightAttachments) name="search-archive">
    {
        // convert req. params to a multimap, so that the rest of the code doesn't have to deal with httprequest directly
        Multimap<String, String> params = JSPHelper.convertRequestToMap(request);
        SearchResult inputSet = new SearchResult(archive,params);
        Pair<Collection<Document>,SearchResult> search_result = SearchResult.selectDocsAndBlobs(inputSet);
        //Pair<Collection<Document>, Collection<Blob>> search_result
        docs = search_result.first;
        //Collections.sort(docs);//order by time
        outputSet = search_result.second;
    }
    //</editor-fold>


    Map<String, Collection<DetailedFacetItem>> facets;
    //<editor-fold desc="Create facets(categories) based on the search data" input="docs;archive"
    // output="facets" name="search-create-facets">
    {
        facets = IndexUtils.computeDetailedFacets(docs, archive);
    }
    //</editor-fold>

    String origQueryString = request.getQueryString();
    //<editor-fold desc="Massaging the query string (containing all search parameter)" input="request"
    // output="string:origQueryString" name="search-query-string-massaging" >
    {
        if (origQueryString == null)
            origQueryString = "";

        // make sure adv-search=1 is present in the query string since we've now switched over to the new searcher
        if (origQueryString.indexOf("adv-search=") == -1) {
            if (origQueryString.length() > 0)
                origQueryString += "&";
            origQueryString += "adv-search=1";
        }

        // remove all the either's because they are not needed, and could mask a real facet selection coming in below
        origQueryString = Util.excludeUrlParam(origQueryString, "direction=either");
        origQueryString = Util.excludeUrlParam(origQueryString, "mailingListState=either");
        origQueryString = Util.excludeUrlParam(origQueryString, "reviewed=either");
        origQueryString = Util.excludeUrlParam(origQueryString, "doNotTransfer=either");
        origQueryString = Util.excludeUrlParam(origQueryString, "transferWithRestrictions=either");
        origQueryString = Util.excludeUrlParam(origQueryString, "attachmentExtension=");
        origQueryString = Util.excludeUrlParam(origQueryString, "entity=");
        origQueryString = Util.excludeUrlParam(origQueryString, "correspondent=");
        origQueryString = Util.excludeUrlParam(origQueryString, "attachmentFilename=");
        origQueryString = Util.excludeUrlParam(origQueryString, "attachmentExtension=");
        origQueryString = Util.excludeUrlParam(origQueryString, "annotation=");
        origQueryString = Util.excludeUrlParam(origQueryString, "folder=");
        // entity=&correspondent=&correspondentTo=on&correspondentFrom=on&correspondentCc=on&correspondentBcc=on&attachmentFilename=&attachmentExtension=&annotation=&startDate=&endDate=&folder=&lexiconName=general&lexiconCategory=Award&attachmentExtension=gif
    }
    //</editor-fold>


    if (Util.nullOrEmpty(docs)) { %>
<div style="margin-top:2em;font-size:200%;text-align:center;">No matching messages.</div>
<%} else { %>
<div class="browsepage" style="min-width:1220px">

    <!-- 160px block on the left for facets -->
    <div style="display:inline-block;vertical-align:top;width:160px;padding-left:5px">
        <div class="facets" style="min-width:10em;text-align:left;margin-bottom:0px;">
            <%
                if (!Util.nullOrEmpty(term)) {
                    out.println("<b>Search</b><br/>\n");
                    String displayTerm = Util.ellipsize(term, 15);

                    out.println("<span title=\"" + Util.escapeHTML(term) + "\" class=\"facet nojog selected-facet rounded\" style=\"padding-left:2px;padding-right:2px\">" + Util.escapeHTML(displayTerm));
                    out.println (" <span class=\"facet-count\">(" + docs.size() + ")</span>");
                    out.println ("</span><br/>\n");
                    out.println("<br/>\n");
                }
                //<editor-fold desc="Facet-rendering" input="facets" output="html:out">
                for (String facet: facets.keySet())
                {
                    List<DetailedFacetItem> items = new ArrayList<DetailedFacetItem>(facets.get(facet));
                    if (items.size() == 0)
                        continue; // don't show facet if it has no items.

                    // the facet items could still all have 0 count, in which case, skip this facet
                    boolean nonzero = false;
                    for (DetailedFacetItem f: items)
                        if (f.totalCount() > 0)
                        {
                            nonzero = true;
                            continue;
                        }
                    if (!nonzero)
                        continue;

                    String facetTitle = Util.escapeHTML(facet);
                    if ("correspondent".equals(facetTitle))
                        facetTitle = "correspondents";

                    facetTitle = Util.capitalizeFirstLetter(facetTitle);
                    out.println("<b>" + facetTitle + "</b><br/>\n");
                    Collections.sort(items);

                    // generate html for each facet. selected and unselected facets separately
                    List<String> htmlForSelectedFacets = new ArrayList<String>();
                    List<String> htmlForUnSelectedFacets = new ArrayList<String>();

                    // random idea: BUI (Blinds user interface. provide blinds-like controls (pull a chain down/to-the-side to reveal information))
                    for (DetailedFacetItem f: items)
                    {
                        if (f.totalCount() == 0)
                            continue;
                        // find the facet url in the facet params
                        int facetParamIdx = Util.indexOfUrlParam(origQueryString, f.messagesURL); // TODO: do we need to worry about origQueryString.replaceAll("%20", " ")?
                        boolean facetAlreadySelected = facetParamIdx >= 0;
                        if (Util.nullOrEmpty(f.name))
                        {
                            JSPHelper.log.info ("Warning; empty title!"); /* happened once */
                            continue;
                        }
                        String name = Util.ellipsize(f.name, 15);
                        String url = request.getRequestURI();
                        // f.url is the part that is to be added to the current url
                        if (!Util.nullOrEmpty(origQueryString))
                        {
                            if (!facetAlreadySelected)
                                url += "?" + origQueryString + "&" + f.messagesURL;
                            else
                                // facet filter already selected, so unselect it
                                url += "?" + Util.excludeUrlParam(origQueryString, f.messagesURL);
                        }
                        else
                        {
                            // no existing params ... not sure if this can happen (might some day if we want
                            // to browse all messages in session)
                            url += '?' + f.messagesURL;
                        }

                        String c = facetAlreadySelected ? " selected-facet rounded" : "";

                        String html = "<span class=\"facet nojog" + c + "\" style=\"padding-left:2px;padding-right:2px\" onclick=\"javascript:self.location.href='" + url + "';\" title=\"" + Util.escapeHTML(f.description) + "\">" + Util.escapeHTML(name)
                                + " <span class=\"facet-count\">(" + f.totalCount() + ")</span>"
                                + "</span><br/>\n";
                        if (facetAlreadySelected)
                            htmlForSelectedFacets.add(html);
                        else
                            htmlForUnSelectedFacets.add(html);
                    }

                    // prioritize selected over unselected facets
                    List<String> htmlForAllFacets = new ArrayList<String>();
                    htmlForAllFacets.addAll(htmlForSelectedFacets);
                    htmlForAllFacets.addAll(htmlForUnSelectedFacets);

                    int N_INITIAL_FACETS = 5;
                    // toString the first 5 items, rest hidden under a More link
                    int count = 0;
                    for (String html: htmlForAllFacets)
                    {
                        out.println (html);
                        if (++count == N_INITIAL_FACETS && htmlForAllFacets.size() > N_INITIAL_FACETS)
                            out.println("<div style=\"display:none;margin:0px;\">\n");
                    }

                    if (count > N_INITIAL_FACETS)
                    {
                        out.println("</div>");
                        out.println("<div class=\"clickableLink\" style=\"text-align:right;padding-right:10px;font-size:80%\" onclick=\"muse.reveal(this)\">More</div>\n");
                    }
                    out.println ("<br/>");
                    out.flush();
                } // String facet
                //</editor-fold>
            %>
        </div> <!--  .facets -->
    </div> <!-- 160px block -->

    <!-- 1020px block for the actual message body -->
    <%
        //parameterizing the class name so that any future modification is easier
        String jog_contents_class = "message";
    %>
    <div style="display:inline-block;vertical-align:top;">
        <div class="browse_message_area rounded shadow;position:relative" style="width:1020px;min-height:400px">
            <div class="controls" style="position:relative;width:100%;">

                <div style="position:relative;float:left;padding:5px;">
                    <% if (ModeConfig.isAppraisalMode() || ModeConfig.isProcessingMode()) { %>
                    <i title="Do not transfer" id="doNotTransfer" class="flag fa fa-ban"></i>
                    <i title="Transfer with restrictions" id="transferWithRestrictions" class="flag fa fa-exclamation-triangle"></i>
                    <% } %>
                    <% if (ModeConfig.isAppraisalMode() || ModeConfig.isProcessingMode() || ModeConfig.isDeliveryMode()) { %>
                    <i title="Message Reviewed" id="reviewed" class="flag fa fa-eye"></i>
                    <% } %>
                    <% if (ModeConfig.isDeliveryMode()) { %>
                    <i title="Add to Cart" id="addToCart" class="flag fa fa-shopping-cart"></i>
                    <% } %>

                    <% if (ModeConfig.isAppraisalMode() || ModeConfig.isProcessingMode() || ModeConfig.isDeliveryMode()) { %>
                    <div style="display:inline;z-index:1000;" id="annotation_div">
                        <input id="annotation" placeholder="Annotation" style="z-index:1000;width:20em;margin-left:25px"/>
                    </div>
                    <% } %>
                    <% if (ModeConfig.isAppraisalMode() || ModeConfig.isProcessingMode() || ModeConfig.isDeliveryMode()) { %>
                    <!--
                    <button type="button" class="btn btn-default" style="margin-left:25px;margin-right:25px;" id="apply">Apply <img class="spinner" style="height:14px;display:none" src="images/spinner.gif"></button>
                    -->
                    <%
                        // show apply to all only if > 1 doc
                        if (docs.size() > 1) { %>
                    <button type="button" class="btn btn-default" id="applyToAll" style="margin-left:25px;margin-right:25px;">Apply to all <img class="spinner" style="height:14px;display:none" src="images/spinner.gif"></button>
                    <% } %>
                    <% } %>
                </div>

                <div style="float:right;position:relative;top:8px">
                    <div>
                        <div style="display:inline;vertical-align:top;font-size:20px; position:relative; top:8px; margin-right:10px" id="pageNumbering"></div>
                        <ul class="pagination">
                            <li class="button">
                                <a id="page_back" style="border-right:0" href="#0" class="icon-peginationarrow"></a>
                                <a id="page_forward" href="#0" class="icon-circlearrow"></a>
                            </li>
                        </ul>
                    </div>
                    <!--
                    <img src="images/back_enabled.png" id="back_arrow"/>
                    <img src="images/forward_enabled.png" id="forward_arrow"/>
                    -->
                    <!--
                         <div class="pagination">
                             <li><a id="back_arrow" style="border-right:0" href="#0" class="icon-peginationarrow"></a></li>
                             <li> <div style="display:inline;" id="pageNumbering"></div></li>
                             <li><a id="forward_arrow" href="#0" class="icon-circlearrow"></a></li>
                         </div>
                         -->
                </div>
                <div style="clear:both"></div>
            </div> <!-- controls -->

            <!--  to fix: these image margins and paddings are held together with ducttape cos the orig. images are not a consistent size -->
            <!-- <span id="jog_status1" class="showMessageFilter rounded" style="float:left;opacity:0.5;margin-left:30px;margin-top:10px;">&nbsp;0/0&nbsp;</span>  -->
            <div style="font-size:12pt;opacity:0.5;margin-left:30px;margin-right:30px;margin-top:10px;">
                <!--	Unique Identifier: <span id="jog_docId" title="Unique ID for this message"></span> -->
            </div>
            <div style="clear:both"></div>
            <div id="jog_contents" style="position:relative" class="<%=jog_contents_class%>">
                <div style="text-align:center"><h2>Loading <%=Util.commatize(docs.size())%> messages <img style="height:20px" src="images/spinner.gif"/></h2></div>
            </div>
        </div>
    </div>
    <%

        // now if filter is in effect, we highlight the filter word too
        //as it is a common highlighting information valid for all result documents we add it to
        //commonHLInfo object of outputset by calling appropriate API.
        /*NewFilter filter = (NewFilter) JSPHelper.getSessionAttribute(session, "currentFilter");
        if (filter != null && filter.isRegexSearch()) {
            outputSet.addCommonHLInfoTerm(filter.get("term"));
        }
        */
        Pair<DataSet, String> pair = null;
        try {
            String sortBy = request.getParameter("sortBy");

            // note: we currently do not support clustering for "recent" type, only for the chronological type. might be easy to fix if needed in the future.
            if ("recent".equals(sortBy) || "relevance".equals(sortBy) || "chronological".equals(sortBy))
                pair = EmailRenderer.pagesForDocuments(outputSet, datasetName, MultiDoc.ClusteringType.NONE);
            else {
                // this path should not be used as it sorts the docs in some order
                // (old code meant to handle clustered docs, so that tab can jump from cluster to cluster. not used now)
                pair = EmailRenderer.pagesForDocuments(outputSet, datasetName);
            }
        }catch(Exception e){
            Util.print_exception("Error while making a dataset out of docs", e, JSPHelper.log);
        }

        DataSet browseSet = pair.getFirst();
        String html = pair.getSecond();

        // entryPct says how far (what percentage) into the selected pages we want to enter
        int entryPage = IndexUtils.getDocIdxWithClosestDate((Collection) docs,
                HTMLUtils.getIntParam(request, "startMonth", -1), HTMLUtils.getIntParam(request, "startYear", -1));
        if (entryPage < 0) {
            // if initdocid is set, look for a doc with that id to set the entry page
            String docId = request.getParameter("initDocId");
            int idx = 0;
            for (Document d: docs)
            {
                if (d.getUniqueId().equals(docId))
                    break;
                idx++;
            }
            if (idx < docs.size()) // means it was found
                entryPage = idx;
            else
                entryPage = 0;
        }
        out.println ("<script type=\"text/javascript\">var entryPage = " + entryPage + ";</script>\n");

        session.setAttribute (datasetName, browseSet);
        //session.setAttribute ("docs-" + datasetName, new ArrayList<>(docs));
        out.println (html);
        JSPHelper.log.info ("Browsing " + browseSet.size() + " pages in dataset " + datasetName);

    %>
</div> <!--  browsepage -->
<br/>
<% } %> <!--  Util.nullOrEmpty(docs)  -->
<br/>

<!-- a couple of confirm modals that can be invoked when necessary -->
<div>
    <div id="info-modal" class="modal fade" style="z-index:9999">
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header">
                    <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
                    <h4 class="modal-title">Confirm</h4>
                </div>
                <div class="modal-body">
                </div>
                <div class="modal-footer">
                    <button id='append-button' type="button" class="btn btn-default" data-dismiss="modal">Append</button>
                    <button id='overwrite-button' type="button" class="btn btn-default" data-dismiss="modal">Overwrite</button>
                    <button id='cancel-button' type="button" class="btn btn-default" data-dismiss="modal">Cancel</button>
                </div>
            </div><!-- /.modal-content -->
        </div><!-- /.modal-dialog -->
    </div><!-- /.modal -->

    <div id="info-modal1" class="modal fade" style="z-index:9999">
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header">
                    <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
                    <h4 class="modal-title">Confirm</h4>
                </div>
                <div class="modal-body">
                </div>
                <div class="modal-footer">
                    <button id='no-button' type="button" class="btn btn-default" data-dismiss="modal">Leave flags unchanged</button>
                    <button id='yes-button' type="button" class="btn btn-default" data-dismiss="modal">Continue</button>
                </div>
            </div><!-- /.modal-content -->
        </div><!-- /.modal-dialog -->
    </div><!-- /.modal -->
</div>

<div style="clear:both"></div>
<jsp:include page="footer.jsp"/>
</body>
</html>
