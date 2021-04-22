const fetch = require('node-fetch')
const fs = require('fs/promises')
const { once } = require('events')

function getNewest (arr) {
  return arr.reduce((prev, current) => {
    return prev.build > current.build ? prev : current
  }, {})
}

async function start () {
  const [,,wantedVersion] = process.argv
  const res = await fetch('https://meta.fabricmc.net/v2/versions/yarn/')
  const json = await res.json()
  const filtered = json.filter(x => x.gameVersion === wantedVersion)
  const version = getNewest(filtered)?.version
  if (!version) {
    console.error('There was a problem with the version supplied.')
    return
  }
  const text = `minecraft_version=${wantedVersion}\nyarn_mappings=${version}`
  await fs.writeFile('gradle.properties', text)
  console.info('Config file written.')
  const child = require('child_process').exec('gradle run')
  // child.stdout.pipe(process.stdout) // if you want the output of gradle
  await once(child, 'exit')
  console.info('Gradle done.')
}

start()
