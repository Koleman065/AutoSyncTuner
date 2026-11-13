package com.edk.autosynctuner

/** Trimmed copies of real AutoSync Manager pages (security token replaced). */
object Fixtures {
    val VEHICLE = """<html><body><div class="col-sm-3"><i>Select a module to view available options.</i><div class="panel-group" id="accordion"><div class="panel panel-default"><div class="panel-heading"><h4 class="panel-title"><a class="accordion-toggle " style="display:block" data-toggle="collapse" data-parent="#accordion" href="#BCM"> BCM</a></h4></div><div id="BCM" class="panel-collapse collapse in"><div class="panel-body"><div class="panel panel-default"><div class="panel-heading" role="tab" id="hdg-1-bcm"><h4 class="panel-title"><a class="accordion-toggle collapsed" style="display:block" data-toggle="collapse" data-parent="#accordion-bcm" href="#collapse-1-bcm"> Approach Lighting </a></h4></div><div id="collapse-1-bcm" class="panel-collapse collapse " role="tabpanel"><ul class="list-group"><li class="list-group-item"><a style="display:block" href="/tuning/1GC1KWEY5JF237761/359/">Approach Lighting Theme</a></li><li class="list-group-item"><a style="display:block" href="/tuning/1GC1KWEY5JF237761/235/">Approach Lights Ambient Light Check</a></li></ul></div></div><div class="panel panel-default"><div class="panel-heading" role="tab" id="hdg-2-bcm"><h4 class="panel-title"><a class="accordion-toggle collapsed" style="display:block" data-toggle="collapse" data-parent="#accordion-bcm" href="#collapse-2-bcm"> Daytime Running Lights </a></h4></div><div id="collapse-2-bcm" class="panel-collapse collapse " role="tabpanel"><ul class="list-group"><li class="list-group-item"><a style="display:block" href="/tuning/1GC1KWEY5JF237761/6/">DRL Style</a></li></ul></div></div></div></div></div><div class="panel panel-default"><div class="panel-heading"><h4 class="panel-title"><a class="accordion-toggle collapsed" style="display:block" data-toggle="collapse" data-parent="#accordion" href="#IPC"> IPC</a></h4></div><div id="IPC" class="panel-collapse collapse"><div class="panel-body"><div class="panel panel-default"><div class="panel-heading" role="tab" id="hdg-5-ipc"><h4 class="panel-title"><a class="accordion-toggle collapsed" style="display:block" data-toggle="collapse" data-parent="#accordion-ipc" href="#collapse-5-ipc"> System </a></h4></div><div id="collapse-5-ipc" class="panel-collapse collapse " role="tabpanel"><ul class="list-group"><li class="list-group-item"><a style="display:block" href="/tuning/1GC1KWEY5JF237761/434/">Coolant Temp Gauge Scaling</a></li></ul></div></div></div></div></div></div></div><div class="col-sm-9"></div></body></html>
"""

    val GRID_BINARY_359 = """<html><body><div class="container"><div class="col-sm-3"><i>sidebar</i></div><div class="col-sm-9"><div id="msg"></div><h3>Approach Lighting - Approach Lighting Theme</h3><p>"Approach Lighting" is the feature that automatically illuminates the vehicle interior and exterior lights when the keyfob "unlock" button is pressed at night/darkness.</p><p>-Cornering lights (if equipped)<br>-DRL (Daytime Running Lights)<br>-Fog lights</p><div><b>Stock Value:</b> [[0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 0.0]]</div><div><b>Range:</b> 0 – 1 ___ 0 = No, 1 = Yes</div><div style="padding-top:10px;"><form action="#msg" method="post"><input type="hidden" name="csrfmiddlewaretoken" value="TESTTOKEN"><link href="/static/calibration/css/map.css" media="all" rel="stylesheet"><script src="/static/calibration/js/ag-grid/ag-grid-community.min.js"></script><script src="/static/calibration/js/map.js"></script><input type="hidden" id="map-data" name="custom_map" value="[[0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 1.0, 1.0, 0.0]]"><div class="row"><center><b>Lamps</b></center></div><div class="row" style="margin-bottom: 5px;"><div id="myGrid" class="ag-theme-alpine" style="float: left; width: calc(100% - 20px);"></div></div><script>
document.addEventListener('DOMContentLoaded', function () {
 var gridDiv = document.querySelector('#myGrid');
 new agGrid.Grid(gridDiv, gridOptions);
 gridOptions.min_value = 0.00
 gridOptions.max_value = 1.00
 gridOptions.api.setColumnDefs([
 { field: '0', headerName: 'Cornering'},
 { field: '1', headerName: 'DRL'},
 { field: '2', headerName: 'Fog'},
 { field: '3', headerName: 'High Beam'},
 { field: '4', headerName: 'Low Beam'},
 { field: '5', headerName: 'Park'},
 { field: '6', headerName: 'Puddle'},
 { field: '7', headerName: 'Reverse'},
 { field: '8', headerName: 'Turn'},
 ])
 gridOptions.api.setRowData([{},])
 });
</script><div class="form-group"><div id="div_id_enabled" class="checkbox"><label for="id_enabled" class=""><input type="checkbox" name="enabled" class="checkboxinput" id="id_enabled" checked=""> Setting Enabled </label></div></div><div class="form-group"><div class="controls "><input type="submit" name="update" value="Update" class="btn btn-primary btn-success" id="submit-id-update"><input type="submit" name="delete" value="Delete" class="btn btn-primary btn-danger" id="submit-id-delete"></div></div></form></div></div></div></body></html>
"""

    val GRID_ROWS_434 = """<html><body><div class="container"><div class="col-sm-3"><i>sidebar</i></div><div class="col-sm-9"><div id="msg"></div><h3>System - Coolant Temp Gauge Scaling</h3><p>This map converts the actual true engine coolant temp value as received from the ECM via CAN, to a buffered value that is displayed to the driver on the coolant temp gauge.</p><div><b>Stock Value:</b> [[-40.0, 40.0, 63.0, 85.0, 102.0, 115.0, 125.0, 128.0], [75.0, 75.0, 88.0, 100.0, 100.0, 112.0, 125.0, 128.0]]</div><div><b>Range:</b> -40 – 215 Degrees (C)</div><div style="padding-top:10px;"><form action="#msg" method="post"><input type="hidden" name="csrfmiddlewaretoken" value="TESTTOKEN"><input type="hidden" id="map-data" name="custom_map" value="[[-40.0, 40.0, 63.0, 85.0, 102.0, 115.0, 125.0, 128.0], [75.0, 75.0, 88.0, 100.0, 100.0, 112.0, 125.0, 128.0]]"><div class="row"><center><b>Range</b></center></div><div class="row" style="margin-bottom: 5px;"><div id="myGrid" class="ag-theme-alpine"></div></div><script>
document.addEventListener('DOMContentLoaded', function () {
 gridOptions.min_value = -40.00
 gridOptions.max_value = 215.00
 gridOptions.api.setColumnDefs([
 { field: 'idx', headerName: '', width: '40px', pinned: 'left', editable: false, cellStyle: null},
 { field: '0', headerName: '1'},
 { field: '1', headerName: '2'},
 { field: '2', headerName: '3'},
 { field: '3', headerName: '4'},
 { field: '4', headerName: '5'},
 { field: '5', headerName: '6'},
 { field: '6', headerName: '7'},
 { field: '7', headerName: '8'},
 ])
 gridOptions.api.setRowData([{'idx': 'CAN'}, {'idx': 'Displayed'}])
 gridOptions.columnApi.autoSizeColumns(["idx"]);
 });
</script><div class="form-group"><div id="div_id_enabled" class="checkbox"><label for="id_enabled" class=""><input type="checkbox" name="enabled" class="checkboxinput" id="id_enabled" checked=""> Setting Enabled </label></div></div><div class="form-group"><div class="controls "><input type="submit" name="update" value="Create" class="btn btn-primary btn-success" id="submit-id-update"></div></div></form></div></div></div></body></html>
"""

    val NUMBER_13 = """<html><body><div class="container"><div class="col-sm-3"><i>sidebar</i></div><div class="col-sm-9"><div id="msg"></div><h3>Approach Lighting - Approach Lights Timeout</h3><p>"Approach Lighting" is the feature that automatically illuminates the vehicle interior and exterior lights when the keyfob "unlock" button is pressed at night/darkness.</p><p>After a preset time, the approach lighting will automatically turn off.</p><div><b>Stock Value:</b> 30.00 Seconds</div><div><b>Range:</b> 3.00 – 2500.00 Seconds</div><div style="padding-top:10px;"><form action="#msg" method="post"><input type="hidden" name="csrfmiddlewaretoken" value="TESTTOKEN"><div id="div_id_custom_value" class="form-group"><label for="id_custom_value" class="control-label "> Custom Value </label><div class="controls "><input type="number" name="custom_value" step="0.01" min="3.00" max="2500.00" class="numberinput form-control" id="id_custom_value"></div></div><div class="form-group"><div id="div_id_enabled" class="checkbox"><label for="id_enabled" class=""><input type="checkbox" name="enabled" class="checkboxinput" id="id_enabled" checked=""> Setting Enabled </label></div></div><div class="form-group"><div class="controls "><input type="submit" name="update" value="Create" class="btn btn-primary btn-success" id="submit-id-update"></div></div></form></div></div></div></body></html>
"""

    val ENUM_6 = """<html><body><div class="container"><div class="col-sm-3"><i>sidebar</i></div><div class="col-sm-9"><div id="msg"></div><h3>Daytime Running Lights - DRL Style</h3><p>This setting sets the DRL style/type.</p><div><b>Stock Value:</b> Low Beam (reduced intensity)</div><div style="padding-top:10px;"><form action="#msg" method="post"><input type="hidden" name="csrfmiddlewaretoken" value="TESTTOKEN"><div id="div_id_custom_type_enum" class="form-group"><div class="controls "><select name="custom_type_enum" class="select form-control" id="id_custom_type_enum"><option value="" selected="">---------</option><option value="1">Turn Signals</option><option value="3">Low Beam (reduced intensity)</option><option value="4">Low Beam (full intensity)</option><option value="2">Dedicated - LED</option><option value="5">Dedicated w/ Low Beam</option><option value="6">Fog Lights</option><option value="352">Dedicated w/ park lights</option><option value="353">Dedicated w/ park &amp; turn lights</option></select></div></div><div class="form-group"><div id="div_id_enabled" class="checkbox"><label for="id_enabled" class=""><input type="checkbox" name="enabled" class="checkboxinput" id="id_enabled" checked=""> Setting Enabled </label></div></div><div class="form-group"><div class="controls "><input type="submit" name="update" value="Create" class="btn btn-primary btn-success" id="submit-id-update"></div></div></form></div></div></div></body></html>
"""

    val RADIO_276 = """<html><body><div class="container"><div class="col-sm-3"><i>sidebar</i></div><div class="col-sm-9"><div id="msg"></div><h3>Daytime Running Lights - DRL - Full brightness at night</h3><p>From the factory, the DRL's dim at night.</p><div><b>Stock Value:</b> Disabled</div><div style="padding-top:10px;"><form action="#msg" method="post"><input type="hidden" name="csrfmiddlewaretoken" value="TESTTOKEN"><div class="form-group"><div class="custom-control custom-checkbox"><!-- <input type="number" name="custom_value" step="0.01" class="numberinput custom-control-input" id="id_custom_value"> --><div id="custom_value-group" class="btn-group btn-toggle " data-toggle="buttons"><label class="btn btn-default "><input type="radio" name="custom_value" value="0">Disabled
 </label><label class="btn btn-default active"><input type="radio" name="custom_value" value="1" checked="">Enabled
 </label></div></div></div><input type="hidden" name="enabled" value="True" id="id_enabled"><div class="form-group"><div class="controls "><input type="submit" name="update" value="Update" class="btn btn-primary btn-success" id="submit-id-update"><input type="submit" name="delete" value="Delete" class="btn btn-primary btn-danger" id="submit-id-delete"></div></div></form></div></div></div></body></html>
"""

    /** Profile page with two vehicles and two AutoSync modules (second vehicle/device made up). */
    val PROFILE = """<html><body><div class="col-sm-8"><div class="panel panel-default"><div class="panel-heading">Vehicles </div><div class="panel-body"><p><i>Select a vehicle below to access custom tuning.</i></p><ul class="list-group"><li class="list-group-item text-right" style="padding:1px"><a style="display:block;padding:10px" href="/tuning/1GC1KWEY5JF237761/"> <span class="pull-left"><u><strong>1GC1KWEY5JF237761</strong></u></span> 2018 CHEVROLET Silverado 2500 4WD 4D 6.6 Diesel </a></li><li class="list-group-item text-right" style="padding:1px"><a style="display:block;padding:10px" href="/tuning/1GT12UEY0LF123456/"> <span class="pull-left"><u><strong>1GT12UEY0LF123456</strong></u></span> 2020 GMC Sierra 2500 HD 4WD 6.6 Diesel </a></li></ul></div></div><div class="panel panel-default"><div class="panel-heading">AutoSync Devices </div><div class="panel-body"><ul class="list-group"><li class="list-group-item text-right"><span class="pull-left"><strong>13CDF0</strong></span> AutoSync-v3.95 - Update required </li><li class="list-group-item text-right"><span class="pull-left"><strong>2A11B7</strong></span> AutoSync-v4.10 </li></ul></div></div></div></body></html>"""
}
