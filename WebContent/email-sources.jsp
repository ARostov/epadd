<%@page contentType="text/html; charset=UTF-8"%>
<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="edu.stanford.muse.email.AddressBook"%>
<%@page language="java" import="edu.stanford.muse.index.Archive"%>
<%@page language="java" import="java.util.List"%>
<%@page language="java" import="java.util.Set"%>
<%@ page import="edu.stanford.muse.webapp.ModeConfig" %>
<%@page language="java" %>
<!DOCTYPE HTML>
<html>
<head>
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>Specify Email Sources</title>
	<link rel="icon" type="image/png" href="images/epadd-favicon.png">


	<link rel="stylesheet" href="bootstrap/dist/css/bootstrap.min.css">
	<link href="jqueryFileTree/jqueryFileTree.css" rel="stylesheet" type="text/css" media="screen" />
	<jsp:include page="css/css.jsp"/>
	<link rel="stylesheet" href="css/sidebar.css">
	<style>
		.div-input-field { display: inline-block; width: 400px; margin-left: 20px; line-height:10px; padding:20px; vertical-align: top;}
		.input-field {width:350px;}
		.input-field-label {font-size: 12px;}

	</style>

	<script src="js/jquery.js"></script>
	<script type="text/javascript" src="bootstrap/dist/js/bootstrap.min.js"></script>
	<script src="jqueryFileTree/jqueryFileTree.js"></script>
	<script src="js/filepicker.js"></script>
	<script src="js/modernizr.min.js"></script>
	<script src="js/sidebar.js"></script>

	<script src="js/muse.js"></script>
	<script src="js/epadd.js"></script>

</head>
<body style="background-color:white;">
<jsp:include page="header.jspf"/>
<jsp:include page="div_filepicker.jspf"/>

<% if (ModeConfig.isAppraisalMode()) { %>
	<script>epadd.nav_mark_active('Import');</script>
<% } else { %>
	<script>epadd.nav_mark_active('New');</script>
<% } %>

<%@include file="profile-block.jspf"%>

<%
Archive archive =  JSPHelper.getArchive(request);
String bestName = "";
String bestEmail = "";
if (archive != null) {
	AddressBook ab = archive.addressBook;
	Set<String> addrs = ab.getOwnAddrs();
	if (addrs.size() > 0)
		bestEmail = addrs.iterator().next();
	writeProfileBlock(out, archive, "", "Import email into this archive");
}
%>

<!--sidebar content-->
<div class="nav-toggle1 sidebar-icon">
	<img src="images/sidebar.png" alt="sidebar">
</div>

<nav class="menu1" role="navigation">
	<h2>Import Tips</h2>
	<!--close button-->
	<a class="nav-toggle1 show-nav1" href="#">
		<img src="images/close.png" class="close" alt="close">
	</a>

	<!--phrase-->
	<div class="search-tips">
		<img src="images/pharse.png" alt="">
		<p>
			Text from Josh here.
		</p>
	</div>

	<!--requered-->
	<div class="search-tips">
		<img src="images/requered.png" alt="">
		<p>
			More text
		</p>
	</div>
</nav>
<!--/sidebar-->

<p>

<form method="post" class="form-horizontal">
<div id="all_fields" style="margin-left:170px; width:900px; padding: 10px">
	<section>
		<div class="panel">
			<div class="panel-heading">About this archive</div>
			<div class="div-input-field">
				<div class="input-field-label"><i class="fa fa-user"></i> Name of archive owner</div>
				<br/>
				<div class="input-field">
					<input class="form-control" type="text" name="name" value="<%=bestName%>"/>
				</div>
			</div>
			<div class="div-input-field">
				<div class="input-field-label"><i class="fa fa-envelope"></i> Primary email address</div>
				<br/>
				<div class="input-field">
					<input class="form-control" type="text" name="alternateEmailAddrs" value="<%=bestEmail%>"/>
				</div>
			</div>

			<div class="div-input-field">
				<div class="input-field-label"><i class="fa fa-tag"></i> Archive title</div>
				<br/>
				<div class="input-field">
					<input class="form-control" type="text" name="archiveTitle" value=""/>
				</div>
			</div>

		</div>
	</section>

	<section>
	<div id="servers" class="accounts panel">
		<% /* proper field names and ids will be assigned later, when the form is actually submitted */ %>
		<div class="panel-heading">Public Email Accounts (Gmail, Yahoo, Hotmail, Live.com, etc)</div>

		<div class="account">
			<input class="accountType" type="text" style="display:none" id="accountType0" name="accountType0" value="email"/>

			<div class="div-input-field">
				<div class="input-field-label"><i class="fa fa-envelope"></i> Email Address</div>
				<br/>
				<div class="input-field"><input class="form-control" type="text" name="loginName0"/></div>
			</div>

			<div class="div-input-field">
				<div class="input-field-label"><i class="fa fa-key"></i> Password <img class="spinner" id="spinner0" src="images/spinner3-black.gif" width="15" style="margin-left:10px;visibility:hidden"></div>
				<br/>
				<div class="input-field"><input class="form-control" type="password" name="password0"/></div>
			</div>
			<br/>
		</div>
		<br/>

		<button style="margin-left:40px" class="btn-default" onclick="add_server(); return false;"><i class="fa fa-plus"></i> <%=edu.stanford.muse.util.Messages.getMessage("messages", "appraisal.email-sources.another-public-imap")%></button>
		<br/>
		<br/>
	</div> <!--  end servers -->
</section>

	<section>
	<p/>
	<div id="private_servers" class="accounts panel">
		<div class="panel-heading">Private Email IMAP Accounts (Google Apps, university account, corporate account, etc.)<br/>
		</div>
		<p></p>

		<% /* proper field names and ids will be assigned later, when the form is actually submitted */ %>
		<div class="account">
			<input class="accountType" type="text" style="display:none" id="accountType1" name="accountType1" value="email"/>

			<div class="div-input-field">
				<div class="input-field-label"><i class="fa fa-server"></i> IMAP Server</div>
				<br/>
				<div class="input-field"><input class="form-control" type="text" name="server1"/></div>
			</div>
			<br/>

			<div class="div-input-field">
				<div class="input-field-label"><i class="fa fa-envelope"></i> Email Address</div>
				<br/>
				<div class="input-field"><input class="form-control" type="text" name="loginName1"/></div>
			</div>

			<div class="div-input-field">
				<div class="input-field-label"><i class="fa fa-key"></i> Password <img class="spinner" id="spinner1" src="images/spinner3-black.gif" width="15" style="margin-left:10px;visibility:hidden"><br/>
				</div>
				<br/>
				<div class="input-field"><input class="form-control" type="password" name="password1"/></div>
			</div>
			<br/>

		</div>
		<br/>
		<button style="margin-left:40px" class="btn-default" onclick="add_private_server(); return false;"><i class="fa fa-plus"></i> <%=edu.stanford.muse.util.Messages.getMessage("messages", "appraisal.email-sources.another-private-imap")%></button>
		<br/>
		<br/>

	</div> <!--  end servers -->
</section>


	<section>
		<div id="mboxes" class="accounts panel">
			<div class="panel-heading">Mbox files<br/>
			</div>

			<% /* all mboxes folders will be in account divs here */ %>
			<div class="account">
				<input class="accountType" type="text" style="display:none" name="accountType2" value="mbox"/>
				<div class="div-input-field">
					<div class="input-field-label"><i class="fa fa-folder-o"></i> Folder or file location</div>
					<br/>
					<div class="input-field">
						<input class="dir form-control" type="text" name="mboxDir2"/> <br/>
						<button class="browse-button btn-default"><i class="fa fa-file"></i>
							<span>Browse</span>
						</button>
					</div>
					<br/>
				</div>
				<div class="div-input-field">
					<div class="input-field-label"><i class="fa fa-bullseye"></i> Name of email source</div>
					<br/>
					<div class="input-field">
						<input class="form-control" type="text" name="emailSource2" value=""/>
					</div>
				</div>

				<br/>
			</div> <!--  end account -->


			<br/>
			<button  style="margin-left:40px" class="btn-default" onclick="return add_mboxdir(); return false;"><i class="fa fa-plus"></i> Add another folder</button>
			<br/>
			<br/>
		</div>
	</section>




	<div style="text-align:center;margin-top:20px">
		<button class="btn btn-cta" id="gobutton" onclick="epadd.do_logins(); return false">Continue <i class="icon-arrowbutton"></i> </button>
	</div>
</div> <!--  all fields -->


</form>

<p>
<jsp:include page="footer.jsp"/>

	<script>
		function add_server() {
			// clone the first account
			var $logins = $('#servers .account');
			var $clone = $($logins[0]).clone();
			$clone.insertAfter($logins[$logins.length-1]);
			$('input', $clone).val(''); // clear the fields so we don't carry over what the cloned fields had
			epadd.fix_login_account_numbers();
		}

		function add_private_server() {
			// clone the first account
			var $logins = $('#private_servers .account');
			var $clone = $($logins[0]).clone();
			$clone.insertAfter($logins[$logins.length-1]);
			$('input', $clone).val(''); // clear the fields so we don't carry over what the cloned fields had
			$('<br/>').insertAfter($logins[$logins.length-1]);
			epadd.fix_login_account_numbers();
		}
		var fps = []; // array of file pickers, could have multiple open at the same time, in theory.
		var $account0 = $($('#mboxes .account')[0]);
		fps.push(new FilePicker($account0));

		function add_mboxdir() {
			// first close all accounts, in case they have been expanded etc.
			for (var i = 0; i < fps.length; i++) {
				fps[i].close();
			}
			// clone the last account
			var $a = $('#mboxes .account');
			var $lasta = $($a[$a.length-1]);
			var $clone = $lasta.clone();
			$('input', $clone).val(''); // clear the fields so we don't carry over the values of the original fields
			$clone.insertAfter($lasta);
			$('<br/>').insertAfter($lasta);
            epadd.fix_login_account_numbers();

			fps.push(new FilePicker($clone));
			return false;
		}

		$(document).ready(function() {
			$('input[type="text"]').each(function () {
				var field = 'email-source:' + $(this).attr('name');
				if (!field)
					return;
				var value = localStorage.getItem(field);
				$(this).val(value);
			});
		});

	</script>

</body>
</html>
