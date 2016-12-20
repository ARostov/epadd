<%@page contentType="text/html; charset=UTF-8"%>
<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="com.google.gson.*"%>

<html>
<head>
	<title>Import accession</title>
	<link rel="icon" type="image/png" href="images/epadd-favicon.png">
	<link rel="stylesheet" href="bootstrap/dist/css/bootstrap.min.css">
	<link href="jqueryFileTree/jqueryFileTree.css" rel="stylesheet" type="text/css" media="screen" />

	<jsp:include page="css/css.jsp"/>

	<script src="js/jquery.js"></script>
	<script src="jqueryFileTree/jqueryFileTree.js"></script>
	<script type="text/javascript" src="bootstrap/dist/js/bootstrap.min.js"></script>
	<script src="js/filepicker.js" type="text/javascript"></script>
	<script src="js/epadd.js" type="text/javascript"></script>
</head>
<body>
<jsp:include page="header.jspf"/>
<script>epadd.nav_mark_active('Add');</script>

<p>

<section>
	<div id="filepicker" style="width:900px;padding-left:170px">

		<div class="div-input-field">
			<div class="input-field-label"><i class="fa fa-folder-o"></i> Accession folder</div>
			<div class="input-field">
				<input id="sourceDir" class="dir form-control" type="text" name="sourceDir"/> <br/>
				<button onclick="return false;" class="btn-default"><i class="fa fa-file"></i>
					<span>Browse</span>
				</button>
			</div>
			<br/>
			<div class="roots" style="display:none"></div>
			<div class="browseFolder">
				<button onclick="return false;" class="btn-default"><i class="fa fa-file"></i>
					<span>Ok</span>
				</button>
			</div>
			<br/>
		</div>
	</div>

    <br/>
	<br/>
	
	<%
	java.io.File[] rootFiles = java.io.File.listRoots(); 
	List<String> roots = new ArrayList<>();
	for (java.io.File f: rootFiles)
		roots.add(f.toString());
	String json = new Gson().toJson(roots);
	%>
	<script> 
		var roots = <%=json%>;
		var fp = new FilePicker($('#filepicker'), roots);
	</script>
	<div style="text-align: center;">
		<div id="spinner-div" style="text-align:center;display:none"><i class="fa fa-spin fa-spinner"></i></div>
		<button class="btn btn-cta" id="gobutton">Import accession <i class="icon-arrowbutton"></i> </button>
	</div>

	<script type="text/javascript">
		$('#gobutton').click(function(e) {
			epadd.load_archive(e, $('#sourceDir').val());
			// should not reach here, because load_archive redirect, but just in case...
			return false;
		});
	</script>
</section>

 <jsp:include page="footer.jsp"/>
 
</body>
</html>
