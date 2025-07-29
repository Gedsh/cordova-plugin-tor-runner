const fs = require('fs');
const path = require('path');

module.exports = function (context) {

    const kspVersion = context.opts.plugin.pluginInfo.getPreferences(context.opts.projectRoot)['KSP_VERSION'];
    const daggerVersion = context.opts.plugin.pluginInfo.getPreferences(context.opts.projectRoot)['ANDROID_DAGGER_VERSION'];

    if (!context.opts.platforms.includes('android')) return;

    const findLicenseBlockEnd = (text) => {
        const trimmedStart = text.match(/^\s*/)[0].length;
        const commentStart = text.indexOf('/*', trimmedStart);
        const commentEnd = text.indexOf('*/', commentStart);
        return (commentStart === trimmedStart && commentEnd > commentStart)
            ? commentEnd + 2
            : 0;
    };

    const buildScript = 'buildscript {'

    const findBuildscriptStart = (text) => {
        return text.indexOf(buildScript)
    }

    const findBuildscriptEnd = (text) => {
        const start = text.indexOf(buildScript) + buildScript.length - 1;
        if (start === -1) return 0;

        let depth = 0;
        let inString = false;
        for (let i = start; i < text.length; i++) {
            const char = text[i];

            if (char === '"' || char === "'") {
                inString = !inString;
            } else if (!inString) {
                if (char === '{') depth++;
                else if (char === '}') depth--;

                if (depth === 0) return i + 1;
            }
        }
        return 0;
    };

    const gradlePath = path.join(context.opts.projectRoot, 'platforms/android/app/build.gradle');
    if (!fs.existsSync(gradlePath)) {
        console.warn('[KSP Hook] No app/build.gradle found');
        return;
    }

    let gradle = fs.readFileSync(gradlePath, 'utf8');

    let pluginIds = [];
    let match;
    const applyPluginRegex = /^apply\s+plugin:\s*['"]([^'"]+)['"]\s*$/gm;
    while ((match = applyPluginRegex.exec(gradle)) !== null) {
        pluginIds.push(match[1]);
    }

    if (pluginIds.length === 0) {
        console.log('[KSP Hook] No apply plugin lines found — nothing to do.');
        return;
    }

    gradle = gradle.replace(applyPluginRegex, '').trim();

    const kspPluginLine = `id 'com.google.devtools.ksp' version ${kspVersion}`;
    const pluginLines = pluginIds.map(id => `    id '${id}'`);
    pluginLines.push(`    ${kspPluginLine}`);
    const pluginsBlock = `plugins {\n${pluginLines.join('\n')}\n}`;

    const kspDependency = `    ksp "com.google.dagger:dagger-compiler:${daggerVersion}"`;
    if (gradle.includes(kspDependency)) {
        console.log('[KSP Hook] KSP dependency already present.');
    } else {
        gradle = gradle.replace(/^dependencies\s*\{/m, match => `${match}\n${kspDependency}`);
    }

    const buildScriptStart = findBuildscriptStart(gradle);
    const buildScriptEnd = findBuildscriptEnd(gradle) + 1;
    const buildScriptBlock = gradle.slice(buildScriptStart, buildScriptEnd);
    gradle = gradle.slice(0, buildScriptStart) + gradle.slice(buildScriptEnd + 1);

    const licenceBlockEnd = findLicenseBlockEnd(gradle);

    gradle = gradle.slice(0, licenceBlockEnd + 1) + '\n\n'
        + buildScriptBlock + '\n\n'
        + pluginsBlock
        + gradle.slice(licenceBlockEnd)

    const lintMarker = 'lintOptions {';
    const newLint = "      disable 'NullSafeMutableLiveData'";

    if (gradle.includes(lintMarker) && !gradle.includes(newLint.trim())) {
        gradle = gradle.replace(
            lintMarker,
            `${lintMarker}\n${newLint}`
        );
        console.log('[KSP Hook] Added lint disable: NullSafeMutableLiveData');
    } else {
        console.log('[KSP Hook] LintOptions already patched or not found.');
    }

    fs.writeFileSync(gradlePath, gradle, 'utf8');
    console.log('[KSP Hook] KSP plugin injected.');
};
