<%@include file="getArchive.jspf" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <title>Set Images</title>
    <link rel="icon" type="image/png" href="images/epadd-favicon.png">

    <link rel="stylesheet" href="bootstrap/dist/css/bootstrap.min.css">
    <jsp:include page="css/css.jsp"/>

    <script src="js/jquery.js"></script>
    <script type="text/javascript" src="bootstrap/dist/js/bootstrap.min.js"></script>
    <script src="js/epadd.js" type="text/javascript"></script>
</head>
<body>
 <% String archiveID = SimpleSessions.getArchiveIDForArchive(archive);%>
    <jsp:include page="header.jspf"/>

    <script>epadd.nav_mark_active('Collections');</script>

    <!-- need status window on this page because upload might take some time, also to alert to errors -->
    <script type="text/javascript" src="js/statusUpdate.js"></script>
    <%@include file="div_status.jspf"%>

    <div style="margin-left:170px">
        You can upload images that represent the archive here. Only PNG format files are currently supported.
        <p></p>
        <form method="POST" action="upload-images" enctype="multipart/form-data" >
            //adding a hidden input field to pass archiveID to the server. This is a common pattern used to pass
            //archiveID in all those forms where POST was used to invoke the server page.
            <input type="hidden" value="<%=archiveID%>" name="archiveID"/>

            Profile Photo: (Aspect ratio 1:1)<br/>
            <div class="profile-small-img"></div>
            <input type="file" name="profilePhoto" id="profilePhoto" /> <br/><br/>

            Landing Page Photo: (Aspect ratio 4:3)<br/>
            <div class="landing-img" style="background-image:url('serveImage.jsp?archiveID=<%=archiveID%>&file=landingPhoto.png')"></div>
            <br/>
            <br/>
            <input type="file" name="landingPhoto" id="landingPhoto" />
            <br/><br/>

            Banner Image: (Aspect ratio 2.5:1)<br/>
            <div class="banner-img" style="background-image:url('serveImage.jsp?archiveID=<%=archiveID%>&file=bannerImage.png')">
            </div>
            <br/>
            <br/>
            <input type="file" name="bannerImage" id="bannerImage" />
            <br/><br/>
            <button id="upload-btn" class="btn btn-cta">Upload <i class="icon-arrowbutton"></i></button>
        </form>
    </div>

    <jsp:include page="footer.jsp"/>

    </body>
</html>