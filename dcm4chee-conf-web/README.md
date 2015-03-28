# dcm4chee-conf-web
Configuration manager based on dcm4che configuration framework.


Important: this project contains both source of html5 app and the results of processing the html5 app with bower/grunt.
The rationale is to avoid the need to have bower/grunt/nodejs installed when you just need to build the maven artifact.
If you are interested in contributing - make modifications in \dcm4chee-conf-web\src\main\webapp-src, then rebuild html5 with `rungrunt.bat`,
 and only then make a commit, so webapp and webapp-src folders stay in sync.
