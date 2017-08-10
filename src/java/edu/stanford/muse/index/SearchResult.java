package edu.stanford.muse.index;

import com.google.common.collect.Multimap;
import edu.stanford.muse.Config;
import edu.stanford.muse.datacache.Blob;
import edu.stanford.muse.datacache.BlobStore;
import edu.stanford.muse.email.AddressBook;
import edu.stanford.muse.email.Contact;
import edu.stanford.muse.email.MailingList;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import edu.stanford.muse.webapp.JSPHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import javax.mail.internet.InternetAddress;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * VIP class. Performs various search functions.
 */
public class SearchResult {
    private static Log log = LogFactory.getLog(SearchResult.class);

    //Helper function to add key-value pair in a map (especially for terms)
    void enhance(Map<String,Set<String>> map, String key, String term) {
        Set<String> old = map.getOrDefault(key,new HashSet<>());
        // note: we add the term to unstemmed terms as well -- no harm. this
        // is being introduced to fix a query param we had like term=K&L Gates and
        // this term wasn't being highlighted on the page earlier, because it
        // didn't match modulo stemming
        // if the query param has quotes, strip 'em
        // along with phrase in quotes there may be other terms,
        // this method does not handle that.
        old.addAll(IndexUtils.getAllWordsInQuery(term));
        if (term.startsWith("\"") && term.endsWith("\""))
            term = term.substring(1, term.length()-1);
        //highlightTermsUnstemmed.addAll(IndexUtils.getAllWordsInQuery(s));
        old.addAll(IndexUtils.getAllWordsInQuery(term));
        map.put(key,old);

    }

    //class that holds the highlighting information that is applicable to all search result documents.
    //Right now, lexicon words, search term, search regex are examples of this set.
    class CommonHLInfo{
        private Map<String,Set<String>> commonHLInfo = new HashMap<>();
        void addTerm(String term){
            enhance(commonHLInfo,"term",term);
        }
        void addContact(Integer cid){
            enhance(commonHLInfo,"contact",cid.toString());
        }
    }

    public void addCommonHLInfoTerm(String term){
        this.commonHLInfo.addTerm(term);
    }

    public Set<String> getHLInfoContactIDs(){
        return this.commonHLInfo.commonHLInfo.getOrDefault("contact", new HashSet<>());
    }

    public Set<String> getHLInfoTerms(EmailDocument edoc){
        //two types of information on term highlighting. One is in commonHLInfo and other is document specific.
        Set<String> res1 = this.matchedDocs.get(edoc).first.info.getOrDefault("term",new HashSet<>());
        Set<String> res2 = this.commonHLInfo.commonHLInfo.getOrDefault("term",new HashSet<>());
        return Util.setUnion(res1,res2);
     }
    //class that holds the document body specific highlighting information
    class BodyHLInfo{
        private Map<String,Set<String>> info;
        public BodyHLInfo(){
            info = new HashMap<>();
        }
        void addTerm(String term){
            enhance(info,"term",term);
        }
        void addTerms(Set<String> terms){
            for(String term : terms){
                addTerm(term);
            }
        }
    }

    //class that holds document attachment specific highlighting information. Right now the map is only b/w
    //the attachment and null set. As we move to displaying attachment in a viewer then we can enrich more words
    //in the attachment to be highlighted.

    class AttachmentHLInfo{
        private Map<Blob,Set<String>> info;
        public AttachmentHLInfo(){
            info = new HashMap<>();
        }
        void addMultipleInfo(Set<Blob> attachments){
            for(Blob b: attachments)
                info.put(b,null);
        }

    }
    /*
    Instance variables used to represent the documents/blobs and
    request parameters used during search.
     */
    private String regexToHighlight;
    private CommonHLInfo commonHLInfo;
    private Archive archive;
    //query parameters
    private Multimap<String, String> queryParams;
    //Set of documents  where the search params
    //matched. BodyHLInfo and AttachmentHLInfo specify the names of the attachments where they matched
    //and whether the terms matched in both(attachments and doc) or only in one of them
    private Map<Document, Pair<BodyHLInfo,AttachmentHLInfo>> matchedDocs;

    /*
    Constructor for SearchResult. The original archive and the set of
    query parameters are passed as arguments.
     */
    public SearchResult(Archive archive, Multimap<String, String> params) {
        this.archive = archive;
        matchedDocs = new HashMap<>();
        Set<Document> docs = archive.getAllDocsAsSet();
        for (Document doc : docs) {
            if (doc instanceof EmailDocument) {
                EmailDocument edoc = (EmailDocument) doc;
                matchedDocs.put(edoc, new Pair(new BodyHLInfo(), new AttachmentHLInfo()));
            }
        }
        queryParams = params;
        commonHLInfo = new CommonHLInfo();
        regexToHighlight = new String("");
    }

    public SearchResult(Map<Document, Pair<BodyHLInfo,AttachmentHLInfo>> matchedDocs, Archive archive,
                        Multimap<String,String> queryParams, CommonHLInfo commonHLInfo, String regexToHighlight){
        this.queryParams=queryParams;
        this.archive=archive;
        this.matchedDocs=matchedDocs;
        this.commonHLInfo = commonHLInfo;
        this.regexToHighlight = regexToHighlight;
    }
    /*
    Another constructor to create SearchResult object directly by putting in the set of documents and blobs
    passing the filters so far. Note that we do not copy the matched docs, just assign the reference.
     */
    public SearchResult(SearchResult other) {
        this.archive = other.archive;
        this.queryParams = other.queryParams;
        this.matchedDocs = other.matchedDocs;
        this.commonHLInfo = other.commonHLInfo;
        this.regexToHighlight = other.regexToHighlight;
    }

    public String getRegexToHighlight(){
        return regexToHighlight;
    }
    public Archive getArchive(){
        return archive;
    }

    public void clear(){
        commonHLInfo.commonHLInfo.clear();
        matchedDocs.clear();
    }
    /**************************Getter methods for search result and highlighting metadata*************/
    public Set<Document> getDocumentSet(){
        return matchedDocs.keySet();
    }

//    public Set<String> getAttachmentHighlightInformation(Document doc, String key){

    //right now this function only retuns a set of attachments to be highlighted for the given doc.
    public Set<Blob> getAttachmentHighlightInformation(Document doc){
            return matchedDocs.get(doc).second.info.keySet();
    }


    /***************************DOCUMENT AND ATTACHMENT SPECIFIC FILTERS****************************/

    /**
     * returns SearchResult containing docs or attachments which match the given regex term
     * (currently full body only, no attachments)
     * Currently no 'document specific highlighting' information is stored for regex term
     * In future, we would like to fill that information inside this same function. Untill then the
     * information will be filled for all documents at the point when searchResult object is being rendered (browse.jsp)
     * @return SearchResult object
     */
    public static SearchResult searchForRegexTerm(SearchResult inputSet, String regexTerm) {
        // go in the order subject, body, attachment
        Set<Document> docsForTerm = new LinkedHashSet<>();
        Indexer.QueryOptions options = new Indexer.QueryOptions();
        options.setQueryType(Indexer.QueryType.REGEX);

        docsForTerm.addAll(inputSet.archive.docsForQuery(regexTerm, options));
        inputSet.matchedDocs.keySet().retainAll(docsForTerm);// keep only those docs on input
        //which satisfied the regex query

        return inputSet;
    }

    /**
     * returns SearchResult containing docs and attachments matching the given term.
     *
     * @param inputSet Input search result object on which this term filtering needs to be done
     * @param term     term to search for
     * @return
     */
    public static SearchResult searchForTerm(SearchResult inputSet, String term) {
        // go in the order subject, body, attachment
        Set<Document> docsForTerm = new LinkedHashSet<>();
        SearchResult outputSet;
        if ("on".equals(JSPHelper.getParam(inputSet.queryParams, "termSubject"))) {
            Indexer.QueryOptions options = new Indexer.QueryOptions();
            options.setQueryType(Indexer.QueryType.SUBJECT);
            docsForTerm.addAll(inputSet.archive.docsForQuery(term, options));
        }

        if ("on".equals(JSPHelper.getParam(inputSet.queryParams, "termBody"))) {
            Indexer.QueryOptions options = new Indexer.QueryOptions();
            options.setQueryType(Indexer.QueryType.FULL);
            docsForTerm.addAll(inputSet.archive.docsForQuery(term, options));
        } else if ("on".equals(JSPHelper.getParam(inputSet.queryParams, "termOriginalBody"))) { // this is an else because we don't want to look at both body and body original
            Indexer.QueryOptions options = new Indexer.QueryOptions();
            options.setQueryType(Indexer.QueryType.ORIGINAL);
            docsForTerm.addAll(inputSet.archive.docsForQuery(term, options));
        }


        Map<Document, Pair<BodyHLInfo,AttachmentHLInfo>> attachmentSearchResult;
        if ("on".equals(JSPHelper.getParam(inputSet.queryParams, "termAttachments"))) {
            attachmentSearchResult = new HashMap<>();
            Set<Blob> blobsForTerm = inputSet.archive.blobsForQuery(term);
            //iterate over 'all attachments' of docs present in 'inputSet'
            inputSet.matchedDocs.keySet().stream().forEach(d->{
                EmailDocument edoc = (EmailDocument)d;
                Set<Blob> commonAttachments = new HashSet<>(edoc.attachments);
                commonAttachments.retainAll(blobsForTerm);
                //Four cases can happen based on the size of commonAttachments and the fact that whether edoc
                //is present in docsForTerm or not.
                //size    present     meaning
                //0         no         term not found in doc as well in it's attachment, don't keep it in result
                //>0        no         term not found in doc but in attachment, keep it with the name in attachmentHLInfo object
                //>0        yes        term found both in attachment as well as in body, keep its info in bodyHLInfo and attachmentHLInfo object of this document
                //0         yes        term found in body but not in attachment. keep its info in bodyHLInfo only.
                if (commonAttachments.size() > 0) {
                    if (docsForTerm.contains(edoc)) {
                        BodyHLInfo bhlinfo = inputSet.matchedDocs.get(d).first;
                        AttachmentHLInfo attachmentHLInfo = inputSet.matchedDocs.get(d).second;
                        //it means the body and the attachment matched the term. add this information in body highliter/attachment highlighter
                        bhlinfo.addTerm(term);
                        attachmentHLInfo.addMultipleInfo(commonAttachments);
                        attachmentSearchResult.put(d, new Pair(bhlinfo,attachmentHLInfo));
                    } else {
                        //means only attachment matched the term. add this information in attachment highlighter
                        BodyHLInfo bhlinfo = inputSet.matchedDocs.get(d).first;
                        AttachmentHLInfo attachmentHLInfo = inputSet.matchedDocs.get(d).second;
                        attachmentHLInfo.addMultipleInfo(commonAttachments);
                        attachmentSearchResult.put(d, new Pair(bhlinfo,attachmentHLInfo));
                    }
                } else if (commonAttachments.size() == 0 && docsForTerm.contains(d)) {
                    //means the document had the term only in its body and not in the attachment.
                    BodyHLInfo bhlinfo = inputSet.matchedDocs.get(d).first;
                    AttachmentHLInfo attachmentHLInfo = inputSet.matchedDocs.get(d).second;
                    bhlinfo.addTerm(term);
                    attachmentSearchResult.put(d, new Pair(bhlinfo,attachmentHLInfo));
                }
            });
            outputSet = new SearchResult(attachmentSearchResult,inputSet.archive,inputSet.queryParams,
                    inputSet.commonHLInfo,inputSet.regexToHighlight);
        }else {
            //just retain only those document in inputSet.matchedDocs which are present in docsForTerm set.
            inputSet.matchedDocs.keySet().retainAll(docsForTerm);
            outputSet = inputSet;
        }
        //blobsForTerm.retainAll(inputSet.matchInAttachment.second);
        /*
        //query for the docs where these blobs are present. Note that we do not need to search for these blobs in all docs
        //only those present in the input search object (matchInAttachment.first) are sufficient as by our invariant of
        //matchInAttachment, the set of documents where matchInAttachment.second are present is same as matchInAttachment.first.
        Set<Document> blobDocsForTerm = (Set<Document>) EmailUtils.getDocsForAttachments((Collection) inputSet.matchInAttachment.first, blobsForTerm);
        attachmentSearchResult = new Pair(blobDocsForTerm,blobsForTerm);
        */

        //Add term to common highlighting info (as it is without parsing) for highlighting.
        // The term will be in lucene syntax (OR,AND etc.)
        //lucene highlighter will take care of highlighting that.
        outputSet.commonHLInfo.addTerm(term);

        return outputSet;

    }


    /**************************ONLY DOCUMENT SPECIFIC FILTERS****************************************/

    /**
     * returns only the docs from amongst the given ones that matches the query specification for flags.
     *
     * @param inputSet The input search result object on which this filtering needs to be done.
     * @return Another SearchResult object containing filtered messages only.
     */
    public static SearchResult filterForFlags(SearchResult inputSet) {

        //keep on removing those documents from inputSet which do not satisfy the filter conditions.
        //Filter1- whether to keep reviewed or not
        String reviewedValue = JSPHelper.getParam(inputSet.queryParams, "reviewed");

        if (!"either".equals(reviewedValue) && !Util.nullOrEmpty(reviewedValue)) {
            inputSet.matchedDocs = inputSet.matchedDocs.entrySet().stream().filter(entry->{
                EmailDocument edoc = (EmailDocument) entry.getKey();
                if (edoc.reviewed == "yes".equals(reviewedValue))
                    return true;
                else
                    return false;
            }).collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue));
        }


        //Filter2- whether to keep those marked with do not transfer
        String dntValue = JSPHelper.getParam(inputSet.queryParams, "doNotTransfer");
        if (!"either".equals(dntValue) && !Util.nullOrEmpty(dntValue)) {
            inputSet.matchedDocs = inputSet.matchedDocs.entrySet().stream().filter(entry->{
                EmailDocument edoc = (EmailDocument) entry.getKey();
                if (edoc.doNotTransfer == "yes".equals(dntValue))
                    return true;
                else
                    return false;
            }).collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue));
        }

        //Filter3- whether to keep those marked with transfer with restriction
        String twrValue = JSPHelper.getParam(inputSet.queryParams, "transferWithRestrictions");
        if (!"either".equals(twrValue) & !Util.nullOrEmpty(twrValue)) {
            inputSet.matchedDocs = inputSet.matchedDocs.entrySet().stream().filter(entry->{
                EmailDocument edoc = (EmailDocument) entry.getKey();
                if (edoc.transferWithRestrictions == "yes".equals(twrValue))
                    return true;
                else
                    return false;
            }).collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue));
        }


        //Filter4- whether to keep those docs in cart
        String inCartValue = JSPHelper.getParam(inputSet.queryParams, "inCart");
        if (!"either".equals(inCartValue) & !Util.nullOrEmpty(inCartValue)) {
            inputSet.matchedDocs = inputSet.matchedDocs.entrySet().stream().filter(entry->{
                EmailDocument edoc = (EmailDocument) entry.getKey();
                if (edoc.addedToCart == "yes".equals(inCartValue))
                    return true;
                else
                    return false;
            }).collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue));
        }

        String annotationStr = JSPHelper.getParam(inputSet.queryParams, "annotation");
        if (!Util.nullOrEmpty(annotationStr)) {
            Set<String> annotations = Util.splitFieldForOr(annotationStr);
            inputSet.matchedDocs = inputSet.matchedDocs.entrySet().stream().filter(entry->{
                EmailDocument edoc = (EmailDocument) entry.getKey();
                if (!Util.nullOrEmpty(edoc.comment)) {
                    String comment = edoc.comment.toLowerCase();
                    if (annotations.contains(comment))
                        return true;
                    else
                        return false;
                }else
                    return false;
            }).collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue));
       }


        return inputSet;
    }

    private static SearchResult filterForEmailDirection(SearchResult inputSet) {

        AddressBook addressBook = inputSet.archive.addressBook;
        String val = JSPHelper.getParam(inputSet.queryParams, "direction");
        if ("either".equals(val) || Util.nullOrEmpty(val))
            return inputSet;


        //keep on removing those documents from inputSet which do not satisfy the filter conditions.

        boolean direction_in = "in".equals(val), direction_out = "out".equals(val);

        if (addressBook != null)
        {
            inputSet.matchedDocs = inputSet.matchedDocs.entrySet().stream().filter(entry->{
                EmailDocument edoc = (EmailDocument) entry.getKey();
                int sent_or_received = edoc.sentOrReceived(addressBook);
                if (direction_in)
                    if (((sent_or_received & EmailDocument.RECEIVED_MASK) != 0) || sent_or_received == 0) // if sent_or_received == 0 => we neither directly recd. nor sent it (e.g. it could be received on a mailing list). so count it as received.
                        return true;//result.add(ed);
                if (direction_out && (sent_or_received & EmailDocument.SENT_MASK) != 0)
                    return true;//result.add(ed);
                return false;
            }).collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue));
        }

        return inputSet;
    }

    /** returns only the docs matching the given contact id. used by facets, correspondents table, etc */
    private static SearchResult filterForContactId(SearchResult inputSet, String cid) {
        String correspondentName = null;
        AddressBook ab = inputSet.archive.addressBook;
        int contactId = -1;
        try { contactId = Integer.parseInt(cid); } catch (NumberFormatException nfe) { }
        if (contactId >= 0) {
            Contact c = ab.getContact(contactId);
            String name = c.pickBestName();
            correspondentName = name;
        }

        //highlighting info addition
        try {
            int ci = Integer.parseInt(cid);
            //add this information to body highlight object under "contact" key
            inputSet.commonHLInfo.addContact(ci);
        } catch (Exception e) {
            JSPHelper.log.warn(cid + " is not a contact id");
        }
        return filterForCorrespondents(inputSet, correspondentName, true, true, true, true); // for contact id, all the 4 fields - to/from/cc/bcc are enabled
    }

    /** version of filterForCorrespondents which reads settings from params. correspondent name can have the OR separator */
    private static SearchResult filterForCorrespondents(SearchResult inputSet ) {

        String correspondentsStr = JSPHelper.getParam(inputSet.queryParams, "correspondent");
        boolean checkToField = "on".equals(JSPHelper.getParam(inputSet.queryParams, "correspondentTo"));
        boolean checkFromField = "on".equals(JSPHelper.getParam(inputSet.queryParams, "correspondentFrom"));
        boolean checkCcField = "on".equals(JSPHelper.getParam(inputSet.queryParams, "correspondentCc"));
        boolean checkBccField = "on".equals(JSPHelper.getParam(inputSet.queryParams, "correspondentBcc"));

        if (Util.nullOrEmpty(correspondentsStr))
            return new SearchResult(inputSet);

        return filterForCorrespondents(inputSet, correspondentsStr, checkToField, checkFromField, checkCcField, checkBccField);
    }

    /** returns only the docs where the name or email address in the given field matches correspondentsStr in the given field(s).
     * correspondentsStr can be or-delimited and specify multiple strings. */
    public static SearchResult filterForCorrespondents(SearchResult inputSet,  String correspondentsStr, boolean checkToField, boolean checkFromField, boolean checkCcField, boolean checkBccField) {

        Set<Contact> searchedContacts = new LinkedHashSet<>();
        AddressBook ab = inputSet.archive.addressBook;
        Set<String> correspondents = Util.splitFieldForOr(correspondentsStr);

        for (String s : correspondents) {
            Contact c = ab.lookupByEmailOrName(s); // this lookup will normalize, be case-insensitive, etc.
            if (c != null)
                searchedContacts.add(c);
        }

        // keep on removing those documents from allDocs which do not satisfy the filter conditions.

        inputSet.matchedDocs = inputSet.matchedDocs.entrySet().stream().filter(k -> {
            EmailDocument ed = (EmailDocument) k.getKey();
            Set<InternetAddress> addressesInMessage = new LinkedHashSet<>(); // only lookup the fields (to/cc/bcc/from) that have been enabled
            if (checkToField && !Util.nullOrEmpty(ed.to))
                addressesInMessage.addAll((List) Arrays.asList(ed.to));
            if (checkFromField && !Util.nullOrEmpty(ed.from))
                addressesInMessage.addAll((List) Arrays.asList(ed.from));
            if (checkCcField && !Util.nullOrEmpty(ed.cc))
                addressesInMessage.addAll((List) Arrays.asList(ed.cc));
            if (checkBccField && !Util.nullOrEmpty(ed.bcc))
                addressesInMessage.addAll((List) Arrays.asList(ed.bcc));

            for (InternetAddress a : addressesInMessage) {
                Contact c = ab.lookupByEmail(a.getAddress());
                if (c == null)
                    c = ab.lookupByName(a.getPersonal());
                if (c != null && searchedContacts.contains(c)) {
                    return true;
                }
            }
            return false;
        }).collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue));


        return inputSet;
    }

    /* returns only the docs matching params["docId"] -- which could be or-delimiter separated to match multiple docs.
    * used for attachment listing. Consider removing this method in favour of message Ids below. */
    private static SearchResult filterForDocId (SearchResult inputSet) {

        Collection<String> docIds = inputSet.queryParams.get("docId");
        if (Util.nullOrEmpty(docIds))
            return inputSet;

        Set<Document> resultDocs = new HashSet<>();
        for (String docId: docIds) {
            EmailDocument ed = inputSet.archive.docForId (docId);
            if (ed != null)
                resultDocs.add(inputSet.archive.docForId(docId));
        }


        //now keep only those docs in inputSet which are present in resultDocs set.
        inputSet.matchedDocs.keySet().retainAll(resultDocs);
        return inputSet;
    }

    /** returns only the docs matching params[uniqueId], which could have multiple uniqueIds
     * separated by the OR separator */
    private static SearchResult filterForMessageId (SearchResult inputSet) {
        String val = JSPHelper.getParam(inputSet.queryParams, "uniqueId");
        if (Util.nullOrEmpty(val))
            return inputSet;

        //keep on removing those documents from allDocs which do not satisfy the filter conditions.

        Set<String> messageIds = Util.splitFieldForOr(val);

        inputSet.matchedDocs = inputSet.matchedDocs.entrySet().stream().filter(k->{
            EmailDocument ed = (EmailDocument)k.getKey();
            String messageSig = Util.hash (ed.getSignature()); // should be made more efficient by storing the hash inside the ed
            if (!Util.nullOrEmpty (messageSig))
                if (messageIds.contains(messageSig))
                    return true;//resultDocs.add(ed);
            return false;
        }).collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue));

        //return modified inputSet
        return(inputSet);
    }

    /** returns only the docs matching per params[mailingListState].
     * If this value is either, no filtering is done.
     * if set to yes, only docs with at least one address matching a mailing list are returned. */
    private static SearchResult filterForMailingListState(SearchResult inputSet ) {
        String mailingListState = JSPHelper.getParam(inputSet.queryParams, "mailingListState");
        AddressBook ab = inputSet.archive.addressBook;
        if ("either".equals(mailingListState) || Util.nullOrEmpty(mailingListState))
            return inputSet;
        // keep on removing those documents from allDocs which do not satisfy the filter conditions.

        inputSet.matchedDocs = inputSet.matchedDocs.entrySet().stream().filter(k->{
            EmailDocument ed = (EmailDocument)k.getKey();
            Set<InternetAddress> allAddressesInMessage = new LinkedHashSet<>(); // only lookup the fields (to/cc/bcc/from) that have been enabled

            // now check for mailing list state
            if (!Util.nullOrEmpty(ed.to)) {
                allAddressesInMessage.addAll((List) Arrays.asList(ed.to));
            }
            if (!Util.nullOrEmpty(ed.from)) {
                allAddressesInMessage.addAll((List) Arrays.asList(ed.from));
            }
            if (!Util.nullOrEmpty(ed.cc)) {
                allAddressesInMessage.addAll((List) Arrays.asList(ed.cc));
            }
            if (!Util.nullOrEmpty(ed.bcc)) {
                allAddressesInMessage.addAll((List) Arrays.asList(ed.bcc));
            }

            boolean atLeastOneML = false; // is any of these addresses a ML?
            for (InternetAddress a : allAddressesInMessage) {
                Contact c = ab.lookupByEmail(a.getAddress());
                if (c == null)
                    c = ab.lookupByName(a.getPersonal());
                if (c == null)
                    continue; // shouldn't happen, just being defensive

                boolean isMailingList = (c.mailingListState & MailingList.SUPER_DEFINITE) != 0 ||
                        (c.mailingListState & MailingList.USER_ASSIGNED) != 0;

                if (isMailingList && "no".equals(mailingListState))
                    return false;//continue outer; // we don't want mailing lists, but found one associated with this message. therefore this message fails search.

                if (isMailingList)
                    atLeastOneML = true; // mark this message as having at least one ML
            }

            if (!atLeastOneML && "yes".equals(mailingListState))
                return false;//continue outer; // no ML, but search criteria need ML, so ignore this ed

            // ok, this ed satisfies ML criteria
            return true;//result.add(ed);
        }).collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue));


        return inputSet;
    }

    /** returns only the docs matching params[emailSource] */
    private static SearchResult filterForEmailSource (SearchResult inputSet) {
        String val = JSPHelper.getParam(inputSet.queryParams, "emailSource");

        if (Util.nullOrEmpty(val))
            return inputSet;

        Set<String> emailSources = Util.splitFieldForOr(val);
        //keep on removing those documents from inputSet which do not satisfy the filter conditions.

        inputSet.matchedDocs = inputSet.matchedDocs.entrySet().stream().filter(k->{
                    EmailDocument ed = (EmailDocument)k.getKey();
                    if (!Util.nullOrEmpty(ed.emailSource))
                        if (emailSources.contains(ed.emailSource.toLowerCase()))
                            return true;//result.add(ed);
                    return false;
                }).collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue));

        return inputSet;
    }

    /* returns only the docs matching params[folder] (can be or-delimiter separated) */
    private static SearchResult filterForFolder(SearchResult inputSet) {

        String val = JSPHelper.getParam(inputSet.queryParams, "folder");
        if (Util.nullOrEmpty(val))
            return inputSet;

        Set<String> folders = Util.splitFieldForOr(val);

        inputSet.matchedDocs = inputSet.matchedDocs.entrySet().stream().filter(k->{
                    EmailDocument ed = (EmailDocument)k.getKey();
                    if (!Util.nullOrEmpty(ed.folderName))
                        if (folders.contains(ed.folderName.toLowerCase()))
                            return true;//result.add(ed);
                    return false;

        }).collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue));

        return inputSet;
    }

    /** returns only the docs containing params[entity] (can be or-delimiter separated) */
    private static SearchResult filterForEntities(SearchResult inputSet) {
        String val = JSPHelper.getParam(inputSet.queryParams, "entity");
        if (Util.nullOrEmpty(val))
            return inputSet;
        Set<String> entities = Util.splitFieldForOr(val);

        inputSet.matchedDocs = inputSet.matchedDocs.entrySet().stream().filter(k->{
                    EmailDocument ed = (EmailDocument)k.getKey();
                    Set<String> entitiesInThisDoc = new LinkedHashSet<>();
                    // question: should we look at fine entities intead?
                    try {
                        entitiesInThisDoc.addAll(Arrays.asList(inputSet.archive.getAllNamesInDoc(ed, true)).stream().map(n->n.text).collect(Collectors.toSet()));
                    } catch (IOException ioe) {
                        Util.print_exception("Error in reading entities", ioe, log);
                        return false;
                    }
                    entitiesInThisDoc = entitiesInThisDoc.parallelStream().map (s -> s.toLowerCase()).collect(Collectors.toSet());
                    entitiesInThisDoc.retainAll(entities);
                    if (entitiesInThisDoc.size() > 0) {
                        //before returning true also add this information in the document body specific highlighting
                        //object.
                        inputSet.matchedDocs.get(k.getKey()).first.addTerms(entitiesInThisDoc);
                        return true;//result.add(ed);
                    }
                    return false;

        }).collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue));


        return inputSet;
    }



    private static SearchResult filterForEntityType( SearchResult inputSet) {
        String val = JSPHelper.getParam(inputSet.queryParams, "entityType");

        if (Util.nullOrEmpty(val))
            return inputSet;

        Set<String> neededTypes = Util.splitFieldForOr(val);

        Set<Document> docsWithNeededTypes = new LinkedHashSet<>();
        for (String type: neededTypes) {
            short code = Short.parseShort(type);
            docsWithNeededTypes.addAll(inputSet.archive.getDocsWithEntityType(code));
        }

        inputSet.matchedDocs.keySet().retainAll(docsWithNeededTypes);
        return inputSet;
   }


    private static SearchResult filterForDateRange(SearchResult inputSet) {
        String start = JSPHelper.getParam(inputSet.queryParams, "startDate"), end = JSPHelper.getParam(inputSet.queryParams, "endDate");

        if (Util.nullOrEmpty(start) && Util.nullOrEmpty(end))
            return inputSet;

        int startYear = -1, startMonth = -1, startDate = -1, endYear = -1, endMonth = -1, endDate = -1;
        if (!Util.nullOrEmpty(start) || !Util.nullOrEmpty(end)) {
            try {
                List<String> startTokens = Util.tokenize(JSPHelper.getParam(inputSet.queryParams, "startDate"), "-");
                startYear = Integer.parseInt(startTokens.get(0));
                startMonth = Integer.parseInt(startTokens.get(1));
                startDate = Integer.parseInt(startTokens.get(2));
            } catch (Exception e) {
                Util.print_exception("Invalid start date: " + start, e, log);
                return inputSet;
            }

            try {
                List<String> endTokens = Util.tokenize(end, "-");
                endYear = Integer.parseInt(endTokens.get(0));
                endMonth = Integer.parseInt(endTokens.get(1));
                endDate = Integer.parseInt(endTokens.get(2));
            } catch (Exception e) {
                Util.print_exception("Invalid end date: " + end, e, log);
                return inputSet;
            }
        }


        //keep those documents from inputSet.matchedDocuments.keySet() which satisfy the filter conditions.
        Set<Document> filtered = new HashSet<>(IndexUtils.selectDocsByDateRange((Collection)inputSet.matchedDocs.keySet(), startYear, startMonth, startDate, endYear, endMonth, endDate));
        //now keep only those docs in inputSet which are present in allDocs set.
        inputSet.matchedDocs.keySet().retainAll(filtered);
        return inputSet;
    }



    private static SearchResult filterDocsByDate (SearchResult inputSet) {
        String start = JSPHelper.getParam(inputSet.queryParams,"startDate"), end = JSPHelper.getParam(inputSet.queryParams,"endDate");

        if (Util.nullOrEmpty(start) && Util.nullOrEmpty(end))
            return inputSet;

        int startYear = -1, startMonth = -1, startDate = -1, endYear = -1, endMonth = -1, endDate = -1;
        if (!Util.nullOrEmpty(start) || !Util.nullOrEmpty(end)) {
            try {
                List<String> startTokens = Util.tokenize(start, "-");
                startYear = Integer.parseInt(startTokens.get(0));
                startMonth = Integer.parseInt(startTokens.get(1));
                startDate = Integer.parseInt(startTokens.get(2));
            } catch (Exception e) {
                Util.print_exception("Invalid start date: " + start, e, JSPHelper.log);
                return inputSet;
            }

            try {
                List<String> endTokens = Util.tokenize(end, "-");
                endYear = Integer.parseInt(endTokens.get(0));
                endMonth = Integer.parseInt(endTokens.get(1));
                endDate = Integer.parseInt(endTokens.get(2));
            } catch (Exception e) {
                Util.print_exception("Invalid end date: " + end, e, JSPHelper.log);
                return inputSet;
            }
        }

        //keep those documents from allDocs which satisfy the filter conditions.
        Set<Document> filtered = new HashSet<>(IndexUtils.selectDocsByDateRange((Collection) inputSet.matchedDocs.keySet(), startYear, startMonth, startDate, endYear, endMonth, endDate));
        //now keep only those docs in inputSet which are present in allDocs set.
        inputSet.matchedDocs.keySet().retainAll(filtered);
        return inputSet;
    }

    private static SearchResult filterForLexicons(SearchResult inputSet) {
        String lexiconName = JSPHelper.getParam(inputSet.queryParams, "lexiconName");

        Lexicon lexicon;
        if (Util.nullOrEmpty(lexiconName))
            return inputSet;
        lexicon = inputSet.archive.getLexicon(lexiconName);
        if (lexicon == null)
            return inputSet;

        String category = JSPHelper.getParam(inputSet.queryParams, "lexiconCategory");
        if (Util.nullOrEmpty(category))
            return inputSet;

        Set<Document> result = (Set) lexicon.getDocsWithSentiments(new String[]{category}, inputSet.archive.indexer, inputSet.matchedDocs.keySet(), -1, false/* request.getParameter("originalContentOnly") != null */, category);

        //now keep only those docs in inputSet which are present in allDocs set.
        inputSet.matchedDocs.keySet().retainAll(result);

        //Highlighting information that will be applied to all documents. This information is based on the
        //lexicon category option passed from the front end.

        boolean doRegexHighlighting = (lexicon != null) && Lexicon.REGEX_LEXICON_NAME.equals(lexiconName);

        Set<String> selectedPrefixes;
        selectedPrefixes = lexicon == null ? null : lexicon.wordsForSentiments(inputSet.archive.indexer, inputSet.matchedDocs.keySet(),new String[]{category} );
        if (selectedPrefixes != null){
            //add quotes or else, stop words will be removed and highlights single words
            for (String sp : selectedPrefixes)
                if (!doRegexHighlighting && !(sp.startsWith("\"") && sp.endsWith("\""))) // enclose in quotes, but only if not already to avoid excessive quoting. Also, do not add quotes if regex search
                    inputSet.commonHLInfo.addTerm('"' + sp + '"');
                else
                    inputSet.commonHLInfo.addTerm(sp);

        }

        if (doRegexHighlighting && selectedPrefixes != null) {
            inputSet.regexToHighlight = String.join("|", selectedPrefixes);
        }

        return inputSet;
    }

    private static SearchResult filterForSensitiveMessages(SearchResult inputSet) {
        String isSensitive = JSPHelper.getParam(inputSet.queryParams, "sensitive");

        if ("true".equals(isSensitive)) {
            Indexer.QueryType qt = null;
            qt = Indexer.QueryType.REGEX;
            Collection<Document> sensitiveDocs = inputSet.archive.docsForQuery(-1 /* cluster num -- not used */, qt);
            //now keep only those docs in inputSet which are present in sensitiveDocs set.
            inputSet.matchedDocs.keySet().retainAll(sensitiveDocs);

            for (Document d: sensitiveDocs) {
                System.out.println ("MessageHash: " + Util.hash (((EmailDocument) d).getSignature()));
            }
        }
        return inputSet;
    }

    /********************************ATTACHMENT SPECIFIC FILTERS*************************************/

    /** returns only those docs with attachments matching params[attachmentEntity]
     * (this field is or-delimiter separated)
     * Todo: review usage of this and BlobStore.getKeywordsForBlob() */
    private static SearchResult filterForAttachmentEntities(SearchResult inputSet) {
        String val = JSPHelper.getParam(inputSet.queryParams, "attachmentEntity");

        if (Util.nullOrEmpty(val))
            return inputSet;

        val = val.toLowerCase();
        Set<String> entities = Util.splitFieldForOr(val);
        BlobStore blobStore = inputSet.archive.blobStore;

        Map<Document,Pair<BodyHLInfo,AttachmentHLInfo>> outputDocs = new HashMap<>();

        inputSet.matchedDocs.keySet().stream().forEach(k -> {
            EmailDocument ed = (EmailDocument) k;
            //Here.. check for all attachments of ed for match.
            Collection<Blob> blobs = ed.attachments;
            Set<Blob> matchedBlobs = new HashSet<>();
            for (Blob blob : blobs) {
                Collection<String> keywords = blobStore.getKeywordsForBlob(blob);
                if (keywords != null) {
                    keywords.retainAll(entities);
                    if (keywords.size() > 0)//it means this blob is of interest, add it to matchedBlobs.
                        matchedBlobs.add(blob);
                }
            }
            //if matchedBlobs is empty then no need to do anything. just drop this document.
            //else if at least one such attachment was find in this document then add it to attachmentHLInfo
            //of this document
            if (matchedBlobs.size() != 0) {
                BodyHLInfo bhlinfo = inputSet.matchedDocs.get(k).first;
                AttachmentHLInfo attachmentHLInfo = inputSet.matchedDocs.get(k).second;
                attachmentHLInfo.addMultipleInfo(matchedBlobs);
                outputDocs.put(k, new Pair(bhlinfo,attachmentHLInfo));

            }


        });
        return new SearchResult(outputDocs,inputSet.archive,inputSet.queryParams,
                inputSet.commonHLInfo,inputSet.regexToHighlight);
    }

    /** this method is a little more specific than attachmentFilename, which only matches the real filename.
     * it matches a specific attachment, including its numeric blobstore prefix.
     * used when finding message(s) belonging to image wall
     *-- After refactoring: If a doc contains at least one attachment of that name then that document
     * is retained along with that attachment and all other previously selected attachments.*/
    private static SearchResult filterForAttachmentNames(SearchResult inputSet) {
        Collection<String> attachmentTailsList = inputSet.queryParams.get("attachment");
        if (Util.nullOrEmpty(attachmentTailsList))
            return inputSet;
        String[] attachmentTails = attachmentTailsList.toArray(new String[attachmentTailsList.size()]);
        Set<String> neededAttachmentTails = new LinkedHashSet<String>();
        for (String s : attachmentTails)
            neededAttachmentTails.add(s);

        Map<Document,Pair<BodyHLInfo,AttachmentHLInfo>> outputDocs = new HashMap<>();
        inputSet.matchedDocs.keySet().stream().forEach(k -> {
            EmailDocument ed = (EmailDocument) k;
            Set<Blob> matchedBlobs = new HashSet<>();
            for (Blob b : ed.attachments) {
                String url = inputSet.archive.blobStore.getRelativeURL(b);
                String urlTail = Util.URLtail(url);
                if (neededAttachmentTails.contains(urlTail)) {
                    matchedBlobs.add(b);
                }
            }
            //if matchedBlobs is empty then no need to do anything. just drop this document.
            //else if at least one such attachment was find in this document then add it to attachmentHLInfo
            //of this document
            if (matchedBlobs.size() != 0) {
                BodyHLInfo bhlinfo = inputSet.matchedDocs.get(k).first;
                AttachmentHLInfo attachmentHLInfo = inputSet.matchedDocs.get(k).second;
                attachmentHLInfo.addMultipleInfo(matchedBlobs);
                outputDocs.put(k, new Pair(bhlinfo, attachmentHLInfo));

            }

        });

        return new SearchResult(outputDocs,inputSet.archive,inputSet.queryParams,
                inputSet.commonHLInfo,inputSet.regexToHighlight);
    }

    /** will look in the given docs for a message with an attachment that satisfies all the requirements.
     * the set of such messages, along with the matching blobs is returned
     * if no requirements, Pair<docs, null> is returned.
     *
     * @param inputSet
     *
     * @return
     */
    //private static Pair<Set<EmailDocument>, Set<Blob>> updateForAttachments(Set<EmailDocument> docs, Multimap<String, String> params) {
    private static SearchResult filterForAttachments(SearchResult inputSet) {


        String neededFilesize = JSPHelper.getParam(inputSet.queryParams, "attachmentFilesize");
        String neededFilename = JSPHelper.getParam(inputSet.queryParams, "attachmentFilename");
        Collection<String> neededTypeStr = JSPHelper.getParams(inputSet.queryParams, "attachmentType"); // this can come in as a single parameter with multiple values (in case of multiple selections by the user)
        String neededExtensionStr = JSPHelper.getParam(inputSet.queryParams, "attachmentExtension");

        if (Util.nullOrEmpty(neededFilesize) && Util.nullOrEmpty(neededFilename) && Util.nullOrEmpty(neededTypeStr) && Util.nullOrEmpty(neededExtensionStr)) {
            return inputSet;
        }

        // set up the file names incl. regex pattern if applicable
        String neededFilenameRegex = JSPHelper.getParam(inputSet.queryParams, "attachmentFilenameRegex");
        Set<String> neededFilenames = null;
        Pattern filenameRegexPattern = null;
        if ("on".equals(neededFilenameRegex) && !Util.nullOrEmpty(neededFilename)) {
            filenameRegexPattern = Pattern.compile(neededFilename);
        } else {
            if (!Util.nullOrEmpty(neededFilename)) // will be in lower case
                neededFilenames = Util.splitFieldForOr(neededFilename);
        }

        // set up the extensions
        Set<String> neededExtensions = null; // will be in lower case
        if (!Util.nullOrEmpty(neededTypeStr) || !Util.nullOrEmpty(neededExtensionStr))
        {
            // compile the list of all extensions from type (audio/video, etc) and explicitly provided extensions
            neededExtensions = new LinkedHashSet<>();
            if (!Util.nullOrEmpty(neededTypeStr)) {
                // will be something like "mp3;ogg,avi;mp4" multiselect picker gives us , separated between types, convert it to ;
                for (String s: neededTypeStr)
                    neededExtensions.addAll(Util.splitFieldForOr(s));
            }
            if (!Util.nullOrEmpty(neededExtensionStr)) {
                neededExtensions.addAll(Util.splitFieldForOr(neededExtensionStr));
            }
        }


        //Here we could not use stream's forEach beacause lambda expression can not use non-final variables
        //declared outside. Here filenameRegexPattern, neededFilenames were giving error. So changed to
        //iteration.
        Map<Document,Pair<BodyHLInfo,AttachmentHLInfo>> outputDocs = new HashMap<>();
        for (Document k : inputSet.matchedDocs.keySet()) {
            EmailDocument ed = (EmailDocument) k;
            Set<Blob> matchedBlobs = new HashSet<>();
            for (Blob b : ed.attachments) {
                // does it satisfy all 3 requirements? if we find any condition that it set and doesn't match, bail out of the loop to the next blob
                // of course its kinda pointless to specify extension if filename is already specified
                // 1. filename matches?
                if (filenameRegexPattern == null) {
                    // non-regex check
                    if (neededFilenames != null && (b.filename == null || !(neededFilename.contains(b.filename))))
                        continue;
                } else {
                    // regex check
                    if (!Util.nullOrEmpty(neededFilename)) {
                        if (b.filename == null)
                            continue;
                        if (!filenameRegexPattern.matcher(b.filename).find()) // use find rather than matches because we want partial match on the filename, doesn't have to be full match
                            continue;
                    }
                }

                // 2. extension matches?
                //a variable to select if the extensions needed contain others.
                boolean isOtherSelected = neededExtensions.contains("others");
                //get the options that were displayed for attachment types. This will be used to select attachment extensions if the option 'other'
                //was selected by the user in the drop down box of export.jsp.
                List<String> attachmentTypeOptions = Config.attachmentTypeToExtensions.values().stream().map(x -> Util.tokenize(x, ";")).flatMap(col -> col.stream()).collect(Collectors.toList());

                if (neededExtensions != null) {
                    if (b.filename == null)
                        continue; // just over-defensive, if no name, effectively doesn't match
                    String extension = Util.getExtension(b.filename);
                    if (extension == null)
                        continue;
                    extension = extension.toLowerCase();
                    //Proceed to add this attachment only if either
                    //1. other is selected and this extension is not present in the list attachmentOptionType, or
                    //2. this extension is present in the variable neededExtensions [Q. What if there is a file with extension .others?]
                    boolean firstcondition = isOtherSelected && !attachmentTypeOptions.contains(extension);
                    boolean secondcondition = neededExtensions.contains(extension);
                    if (!firstcondition && !secondcondition)
                        continue;
                }

                // 3. size matches?
                long size = b.getSize();

                /*
                // these attachmentFilesizes parameters are hardcoded -- could make it more flexible if needed in the future
                // "1".."5" are the only valid filesizes. If none of these, this parameter not set and we can include the blob
                if ("1".equals(neededFilesize) || "2".equals(neededFilesize) || "3".equals(neededFilesize) ||"4".equals(neededFilesize) ||"5".equals(neededFilesize)) { // any other value, we ignore this param
                    boolean include = ("1".equals(neededFilesize) && size < 5 * KB) ||
                            ("2".equals(neededFilesize) && size >= 5 * KB && size <= 20 * KB) ||
                            ("3".equals(neededFilesize) && size >= 20 * KB && size <= 100 * KB) ||
                            ("4".equals(neededFilesize) && size >= 100 * KB && size <= 2 * KB * KB) ||
                            ("5".equals(neededFilesize) && size >= 2 * KB * KB);
                }
                */
                boolean include = Util.filesizeCheck(neededFilesize, size);
                if (!include)
                    continue;
                // if we reached here, all conditions must be satisfied
                matchedBlobs.add(b);
            }

            //if matchedBlobs is empty then no need to do anything. just drop this document.
            //else if at least one such attachment was find in this document then add it to attachmentHLInfo
            //of this document
            if (matchedBlobs.size() != 0) {
                BodyHLInfo bhlinfo = inputSet.matchedDocs.get(k).first;
                AttachmentHLInfo attachmentHLInfo = inputSet.matchedDocs.get(k).second;
                attachmentHLInfo.addMultipleInfo(matchedBlobs);
                outputDocs.put(k, new Pair(bhlinfo,attachmentHLInfo));

            }
        }

        return new SearchResult(outputDocs,inputSet.archive,inputSet.queryParams,
                inputSet.commonHLInfo,inputSet.regexToHighlight);
    }

    /** this map is used only by attachments page right now, not advanced search.
     * TODO: make adv. search page also use it */
    public static SearchResult selectBlobs (SearchResult inputSet) {
        Collection<Document> docs = inputSet.archive.getAllDocs();

        String neededFilesize = JSPHelper.getParam(inputSet.queryParams,"attachmentFilesize");
        String extensions[] = JSPHelper.getParams(inputSet.queryParams,"attachmentExtension").toArray(new String[0]);
        Set<String> extensionsToMatch = new LinkedHashSet<>(); // should also have lower-case strings, no "." included

        if (!Util.nullOrEmpty(extensions)) {
            extensionsToMatch = new LinkedHashSet<>();
            for (String s: extensions)
                extensionsToMatch.add (s.trim().toLowerCase());
        }

        // or given extensions with extensions due to attachment type
        String types[] = JSPHelper.getParams(inputSet.queryParams,"attachmentType").toArray(new String[0]); // this will have more semicolon separated extensions
        if (!Util.nullOrEmpty(types)) {
            for (String t: types) {
                String exts = Config.attachmentTypeToExtensions.get(t);
                if (exts == null)
                    exts=t;
                //continue;
                //Front end should uniformly pass attachment types as extensions like mp3;mov;ogg etc. Earlier it was passing vide, audio, doc etc.
                //In order to accommodate both cases we first check if there is ampping from the extension type to actual extensions using .get(t)
                //if no such mapping is present then we assume that the input extension types are of the form mp3;mov;ogg and work on that.
                String[] components = exts.split (";");
                for (String c: components) {
                    extensionsToMatch.add (c);
                }
            }
        }

        //a variable to select if the extensions needed contain others.
        boolean isOtherSelected = extensionsToMatch.contains("others");
        //get the options that were displayed for attachment types. This will be used to select attachment extensions if the option 'other'
        //was selected by the user in the drop down box of export.jsp.
        List<String> attachmentTypeOptions = Config.attachmentTypeToExtensions.values().stream().map(x->Util.tokenize(x,";")).flatMap(col->col.stream()).collect(Collectors.toList());

        List<Pair<Blob, EmailDocument>> allAttachments = new ArrayList<>();

        SearchResult outputSet = filterDocsByDate(inputSet);
        //Collection<EmailDocument> eDocs = (Collection) filterDocsByDate (params, new HashSet<>((Collection) docs));
        Map<Document,Pair<BodyHLInfo,AttachmentHLInfo>> outputDocs = new HashMap<>();
        for (Document k : outputSet.matchedDocs.keySet()) {
            EmailDocument ed = (EmailDocument) k;
            Set<Blob> matchedBlobs = new HashSet<>();
            for (Blob b : ed.attachments) {
                if (!Util.filesizeCheck(neededFilesize, b.getSize()))
                    continue;

                if (!(Util.nullOrEmpty(extensionsToMatch))) {
                    Pair<String, String> pair = Util.splitIntoFileBaseAndExtension(b.getName());
                    String ext = pair.getSecond();
                    if (ext == null)
                        continue;
                    ext = ext.toLowerCase();
                    //Proceed to add this attachment only if either
                    //1. other is selected and this extension is not present in the list attachmentOptionType, or
                    //2. this extension is present in the variable neededExtensions [Q. What if there is a file with extension .others?]
                    boolean firstcondition = isOtherSelected && !attachmentTypeOptions.contains(ext);
                    boolean secondcondition = extensionsToMatch.contains(ext);
                    if (!firstcondition && !secondcondition)
                        continue;
                }

                // ok, we've survived all filters, add b
                matchedBlobs.add(b);
            }

            //if matchedBlobs is empty then no need to do anything. just drop this document.
            //else if at least one such attachment was find in this document then add it to attachmentHLInfo
            //of this document
            if (matchedBlobs.size() != 0) {
                BodyHLInfo bhlinfo = inputSet.matchedDocs.get(k).first;
                AttachmentHLInfo attachmentHLInfo = inputSet.matchedDocs.get(k).second;
                attachmentHLInfo.addMultipleInfo(matchedBlobs);
                outputDocs.put(k, new Pair(bhlinfo,attachmentHLInfo));

            }
        }

        //Collections.reverse (allAttachments); // reverse, so most recent attachment is first
        return new SearchResult(outputDocs,inputSet.archive,inputSet.queryParams,
                inputSet.commonHLInfo,inputSet.regexToHighlight);
    }

    /*********************COMBINING EVERYTHING TOGETHER TO CREATE AN ENTRY LEVEL SEARCH FUNCTION*******/

    /**
     * VIP method. Top level API entry point to perform the search in the given archive according to params in the given multimap.
     * params specifies (anded) queries based on term, sentiment (lexicon), person, attachment, docNum, etc.
       returns a collection of docs and blobs matching the query.

     * note2: performance can be improved. e.g., if in AND mode, searches that
     * iterate through documents such as selectDocByTag, getBlobsForAttachments, etc., can take the intermediate resultDocs rather than allDocs.
     * set intersection/union can be done in place to the intermediate resultDocs rather than create a new collection.
     * getDocsForAttachments can be called on the combined result of attachments and attachmentTypes search, rather than individually.

     * note3: should we want options to allow user to choose whether to search only in emails, only in attachments, or both?
     * also, how should we allow variants in combining multiple conditions.
     * there will be work in UI too.

     * note4: the returned resultBlobs may not be tight, i.e., it may include blobs from docs that
     * are not in the returned resultDocs.
     * but for docs that are in resultDocs, it should not include blobs that are not hitting.
     * these extra blobs will not be seen since we only use this info for highlighting blobs in resultDocs.
     */
    public static Pair<Collection<Document>,SearchResult> selectDocsAndBlobs(SearchResult inputSet) throws UnsupportedEncodingException
    {
        // below are all the controls for selecting docs
        SearchResult outResult=inputSet;
        String term = JSPHelper.getParam(inputSet.queryParams, "term");
        if (!Util.nullOrEmpty(term)) {
            outResult = searchForTerm(outResult, term);
        }

        String regexTerm = JSPHelper.getParam(inputSet.queryParams, "regexTerm");
        if (!Util.nullOrEmpty(regexTerm)) {
            outResult = searchForRegexTerm(outResult, regexTerm);
        }

        outResult = filterForAttachments(outResult);
        //function name changed from updateForAttachment to filterForAttachments
        //Pair<Set<EmailDocument>, Set<Blob>> p = updateForAttachments((Set) resultDocs, params);
        outResult = filterForAttachmentNames(outResult);
        outResult = filterForAttachmentEntities(outResult);
        outResult = filterForCorrespondents(outResult);

        // contactIds are used for facets and from correspondents page etc.
        Collection<String> contactIds = inputSet.queryParams.get("contact");
        if (!Util.nullOrEmpty(contactIds)) {
            for (String cid : contactIds)
                outResult = filterForContactId(outResult, cid);
        }

        outResult = filterForDocId(outResult);
        outResult = filterForMessageId(outResult);
        outResult = filterForMailingListState(outResult);
        outResult = filterForEmailDirection(outResult);
        outResult = filterForEmailSource(outResult);
        outResult = filterForFolder(outResult);
        outResult = filterForFlags(outResult);
        outResult = filterForDateRange(outResult);
        outResult = filterForLexicons(outResult);
        outResult = filterForEntities(outResult); // searching by entity is probably the most expensive, so keep it near the end
        outResult = filterForEntityType(outResult);

        //  we don't have sensitive messages now (based on PRESET_REGEX)
        // sensitive messages are just a special lexicon
        // outResult = filterForSensitiveMessages(outResult);
        // now only keep blobs that belong to resultdocs
        // sort per the requirement
        Indexer.SortBy sortBy = Indexer.SortBy.RELEVANCE;
        {
            String sortByStr = JSPHelper.getParam(inputSet.queryParams, "sortBy");
            if (!Util.nullOrEmpty(sortByStr)) {
                if ("relevance".equals(sortByStr.toLowerCase()))
                    sortBy = Indexer.SortBy.RELEVANCE;
                else if ("recent".equals(sortByStr.toLowerCase()))
                    sortBy = Indexer.SortBy.RECENT_FIRST;
                else if ("chronological".equals(sortByStr.toLowerCase()))
                    sortBy = Indexer.SortBy.CHRONOLOGICAL_ORDER;
                else {
                    log.warn("Unknown sort by option: " + sortBy);
                }
            }
        }


        List<Document> resultDocsList = new ArrayList<>(outResult.matchedDocs.keySet());
        if (sortBy == Indexer.SortBy.CHRONOLOGICAL_ORDER)
            Collections.sort(resultDocsList);
        else if (sortBy == Indexer.SortBy.RECENT_FIRST) {
            Collections.sort(resultDocsList);
            Collections.reverse(resultDocsList);
        }



        return new Pair((Collection<Document>)resultDocsList,outResult);
    }
}
