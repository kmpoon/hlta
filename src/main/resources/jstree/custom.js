// this file assumes a variable nodes is used to hold the nodes of the jstree
// and a <div> with id 'jstree' as a placeholder of the tree.

function getInputValue(id, defaultValue) {
	var value = $(id).val();
	if (value.length <= 0)
		value = defaultValue;

	return value;
}

function findLevel(node) {
	var l = node.data.level - 1;
	if (typeof levels[l] == 'undefined')
		levels[l] = [];

	levels[l].push(node)

	$.each(node.children, function(i, v) {
		findLevel(v);
	})
}

// find the node levels in the tree
var levels = [];
$.each(nodes, function(i, v) {
	findLevel(v)
});

function constructTree(n) {
	$("#jstree").jstree({
		"core" : {
			"data" : n,
			"themes" : {
				"icons" : false
			}
		},
		"search": {
            "case_insensitive": true,
            "show_only_matches" : true,
            "show_only_matches_children": true
        },
        "plugins": ["search"]
	});
}

function showLevels(top, bottom) {
	var current = $('#jstree').jstree(true);
	if (typeof current != 'undefined')
		current.destroy();

	for (var i = top; i > bottom; i--) {
		$.each(levels[i - 1], function(i, v) {
			v.state.opened = true;
		})
	}

	for (var i = bottom; i > 0; i--) {
		$.each(levels[i - 1], function(i, v) {
			v.state.opened = false;
		})
	}

	constructTree(levels[top-1]);
}

$(function() {
	constructTree(nodes);

	$('#jstree').on("changed.jstree", function(e, data) {
		console.log(data.selected);
	});

    // set the default values of the levels
    $("#top-input").val(levels.length)
    $("#bottom-input").val(1)
  
    $('#level-button').click( function() {
      var top = getInputValue('#top-input', 1000000)
      var bottom = getInputValue('#bottom-input', 1)
      showLevels(top, bottom);
    })
    
    $("#filter-button").click( function() {
        var searchString = $("#search-input").val();
        $('#jstree').jstree('search', searchString);
    });

    $("#clear-button").click( function() {
        $('#jstree').jstree(true).clear_search();
    	$("#search-input").val("");
    });
});
