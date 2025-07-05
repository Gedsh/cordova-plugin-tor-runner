const exec = require('cordova/exec'),
    channel = require('cordova/channel');


exports.isUseWithTor = function(address, success, error) {
    const settings = this.getSettings();
    if (settings.torMode === this.TorMode.AUTO) {
        exec(success, error, 'TorRunner', 'CHECK_ADDRESS', [{address}]);
    } else if (settings.torMode === this.TorMode.ALWAYS) {
        if (settings.torState === this.TorStatus.STOPPED) {
            exec(null, error, 'TorRunner', 'START_TOR', []);
        }
        success({redirect: true, port: settings.torPort});
    } else if (settings.torMode === this.TorMode.NEVER) {
        if (settings.torState === this.TorStatus.RUNNING) {
            exec(null, error, 'TorRunner', 'STOP_TOR', []);
        }
        success({redirect: false, port: 0});
    }
};

exports.getSettings = function()
{
    return this._settings || {};
};

exports.configure = function (options)
{
    const settings = { ...options };

    if (!this._isAndroid)
        return;

    this._mergeObjects(options, this._settings);
    this._mergeObjects(options, this._defaults);
    this._settings = options;

    cordova.exec(null, null, 'TorRunner', 'SET_CONFIGURATION', [settings]);
};

exports.Bridge = {
    NONE:      "NONE",
    VANILLA:   "VANILLA",
    OBFS3:     "OBFS3",
    OBFS4:     "OBFS4",
    MEEK_LITE: "MEEK_LITE",
    SNOWFLAKE: "SNOWFLAKE",
    WEBTUNNEL: "WEBTUNNEL"
};

exports.TorMode = {
    NEVER:  "NEVER",
    ALWAYS: "ALWAYS",
    AUTO:   "AUTO"
};

exports.TorStatus = {
    STOPPED:  "STOPPED",
    STARTING: "STARTING",
    RUNNING:  "RUNNING"
}

exports._settings = {
    torState: exports.TorStatus.STOPPED
};

exports._defaults = {
    torMode:    exports.TorMode.AUTO,
    torPort:    9051,
    bridgeType: exports.Bridge.SNOWFLAKE
};

exports._pluginInitialize = function()
{
    this._isAndroid = device.platform.match(/^android|amazon/i) !== null;

    const success = (options) =>
    {
        this._mergeObjects(options, this._settings);
        this._mergeObjects(options, this._defaults);
        this._settings = options;
    }

    const error = (error) =>
    {
        console.error(error);
    }

    exec(success, error, 'TorRunner', 'GET_CONFIGURATION', []);
};

channel.onDeviceReady.subscribe(function()
{
    channel.onCordovaInfoReady.subscribe(function() {
        exports._pluginInitialize();
    });
});

exports._mergeObjects = function (options, toMergeIn)
{
    for (const key in toMergeIn)
    {
        if (!options.hasOwnProperty(key))
        {
            options[key] = toMergeIn[key];
        }
    }

    return options;
};
