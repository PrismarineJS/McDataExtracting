const fetch = require('node-fetch')
const parser = require('fast-xml-parser')
const fs = require('fs/promises')
const { once } = require('events')

function getNewest (arr) {
  return arr.reduce((prev, current) => {
    return prev.build > current.build ? prev : current
  }, {})
}

async function start () {
  const [,, wantedVersion] = process.argv
  const yarnMappingsVersion = await getYarnMappingsVersion(wantedVersion)
  const fabricLoomVersion = await getFabricLoomVersion()
  const text = `minecraft_version=${wantedVersion}\nyarn_mappings=${yarnMappingsVersion}\nfabric_loom_version=${fabricLoomVersion}\norg.gradle.jvmargs=-Xmx4G -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8`
  await fs.writeFile('gradle.properties', text)
  console.info('Config file written.')
}

async function getYarnMappingsVersion (wantedVersion) {
  const res = await fetch('https://meta.fabricmc.net/v2/versions/yarn/')
  const json = await res.json()
  const filtered = json.filter(x => x.gameVersion === wantedVersion)
  const yarnMappingsVersion = getNewest(filtered)?.version
  if (!yarnMappingsVersion) {
    console.error('There was a problem with the version supplied.')
  }
  return yarnMappingsVersion
}

async function getFabricLoomVersion () {
  const xmlData = (await (await fetch('https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml')).text())
  return new parser.XMLParser().parse(xmlData).metadata.versioning.release
}

start()
