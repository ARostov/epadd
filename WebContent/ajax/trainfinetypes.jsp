<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@ page import="edu.stanford.muse.ie.FinegrainedEntityRecogniser" %>
<%
FinegrainedEntityRecogniser fer = new FinegrainedEntityRecogniser();
session.setAttribute("statusProvider", fer);
Archive archive = JSPHelper.getArchive(session);
fer.trainNER(archive);
//fer.trainNER();
%>