rm -rf samples/jstree/lib/theme
cd samples/jstree/lib/
ln -s ../resources/tm/hlta/jstree/themes/default theme 
cd -

rm -rf samples/jstree/resources
cd samples/jstree/
ln -s ../../src/main/resources resources
cd -
