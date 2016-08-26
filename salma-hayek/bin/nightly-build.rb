#!/usr/bin/ruby -w

# Typical usage (in the builder's crontab):
# echo nightly-build.rb ~/Projects clean native-dist | bash --login
# echo nightly-build.rb ~/Projects | bash --login
# echo nightly-build.rb | bash --login

require "pathname.rb"

# ----------------------------------------------------------------------------
# Parse command line.
# ----------------------------------------------------------------------------
should_update = true
if ARGV[0] == "--no-update"
  ARGV.shift()
  should_update = false
end
projects_root = Pathname.new(__FILE__).realpath().dirname().dirname().dirname()
if ARGV.empty?() == false
  # Must be an absolute path.
  projects_root = ARGV.shift()
end
targets = ARGV
if targets.empty?()
  # Elliott suggests cleaning by default in a nightly build.
  targets << "clean"
  # There is no well-known name for the default target.  lwm doesn't support "build".
  targets << ""
end

# ----------------------------------------------------------------------------
# Find all revision-controlled projects under "projects_root".
# ----------------------------------------------------------------------------
class Project
  def initialize(directory)
    @directory = directory
  end
  
  def directory()
    return @directory
  end
end

class BazaarProject < Project
  def update()
    system("bzr status ; bzr diff ; bzr update")
  end
end

class MercurialProject < Project
  def update()
    system("hg status ; hg diff ; hg pull && hg update")
  end
end

class SubversionProject < Project
  def update()
    system("svn status ; svn diff ; svn update")
  end
end

class GitProject < Project
  def update()
    system("git status ; git diff ; git pull")
  end
end

projects = []

directoryToClass = {
  ".svn" => SubversionProject,
  ".hg" => MercurialProject,
  ".bzr" => BazaarProject,
  ".git" => GitProject
}
Dir.glob("#{projects_root}/{,*/}{#{directoryToClass.keys().join(",")}}").each() {
  |vcs_directory|
  basename = File.basename(vcs_directory)
  root = File.dirname(vcs_directory)
  projects << directoryToClass[basename].new(root)
}
projects.uniq!()

# ----------------------------------------------------------------------------
# Of the jessies.org projects, salma-hayek must come first. Performance anxiety.
# ----------------------------------------------------------------------------
salma_hayek = projects.find() { |item| item.directory().include?("/salma-hayek/") }
if salma_hayek
  projects.delete(salma_hayek)
  projects.insert(0, salma_hayek)
end

# ----------------------------------------------------------------------------
# Update and build the projects.
# ----------------------------------------------------------------------------
failed_updates = []
failed_builds = []
projects.each() {
  |project|
  project_name = File.basename(project.directory())
  Dir.chdir(project.directory())
  if should_update
    print("-- Updating \"#{project_name}\"\n")
    project.update()
    if $? != 0
      failed_updates << project_name
      next
    end
  end
  if (Pathname.new(project.directory()) + "Makefile").exist?()
    print("-- Building \"#{project_name}\"\n")
    commands = targets.map() {
      |target|
      "make #{target}"
    }
    system(commands.join(" && "))
    if $? != 0
      failed_builds << project_name
    end
  end
}

# ----------------------------------------------------------------------------
# Output a quick summary of how things went.
# ----------------------------------------------------------------------------
puts()
if failed_updates.length() > 0
  puts("Failed updates: #{failed_updates.join(' ')}")
end
if failed_builds.length() > 0
  puts("Failed builds: #{failed_builds.join(' ')}")
end
if failed_updates.length() + failed_builds.length() == 0
  puts("Everything built OK")
end
exit(0)
