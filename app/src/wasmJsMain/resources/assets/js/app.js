
const dropbox = document.getElementById('dropbox');
const fileInput = document.getElementById('fileInput');

dropbox.addEventListener('dragover', handleDragOver);
dropbox.addEventListener('dragleave', handleDragLeave);
dropbox.addEventListener('drop', handleDrop);
dropbox.addEventListener('click', () => fileInput.click());

fileInput.addEventListener('change', event => {

    if (event.target.files.length === 0)
        return;

    handleFile(event.target.files[0]);
});

function handleDragOver(event) {

    event.preventDefault();
    event.dataTransfer.dropEffect = 'copy';

    dropbox.classList.add('highlight');
}

function handleDragLeave(event) {

    event.preventDefault();

    dropbox.classList.remove('highlight');
}

function handleDrop(event) {

    event.preventDefault();
    dropbox.classList.remove('highlight');

    let items = event.dataTransfer.items;

    if (items.length === 0)
        return;

    handleFile(items[0].getAsFile());
}

function handleFile(file) {

    let fileReader = new FileReader()

    fileReader.onload = function (event) {

        let uInt8Bytes = new Uint8Array(event.target.result);

        app.then(exports => {
            exports.default.processFile(uInt8Bytes);
        });
    };

    fileReader.readAsArrayBuffer(file);
}

function toggleBoxContent(boxId) {

    const box = document.getElementById(boxId);

    if (box) {

        const content = box.querySelector('.box-content');

        if (content) {
            content.style.display = content.style.display === 'none' ? 'block' : 'none';
            box.classList.toggle('collapsed', content.style.display === 'none');
        }
    }
}

const unhandledError = (event, error) => {

    if (error instanceof WebAssembly.CompileError) {

        document.getElementById("warning").style.display = "initial";
        document.getElementById("dropbox").style.display = "none";

        // Hide the Webpack overlay
        const webpackOverlay = document.getElementById("webpack-dev-server-client-overlay");
        if (webpackOverlay != null)
            webpackOverlay.style.display = "none";
    }
}

addEventListener("error", (event) => unhandledError(event, event.error));
addEventListener("unhandledrejection", (event) => unhandledError(event, event.reason));

/* Initially collapsed */
toggleBoxContent('xmp-box')
toggleBoxContent('hex-box')
