teiid
=====

Teiid is a data virtualization system that allows applications to use data from multiple, heterogeneous data stores.

## Useful Links
- Website - http://teiid.org
- Documentation - https://teiid.gitbooks.io/documents/content/
- Documentation Project - https://teiid.gitbooks.io
- JIRA Issues -  https://issues.jboss.org/browse/TEIID
- User Forum - https://community.jboss.org/en/teiid?view=discussions
- Wiki - https://community.jboss.org/wiki/TheTeiidProject

## To build Teiid
- install JDK 1.7 or higher
- install maven 3 - http://maven.apache.org/download.html
- Create a github account and fork Teiid

Enter the following:

	$ git clone https://github.com/<yourname>/teiid.git
	$ cd teiid
	$ mvn clean install -P release -Dmaven.javadoc.skip=true -s settings.xml
	
you can find the deployment artifacts in the "teiid/build/target" directory once the build is completed.

