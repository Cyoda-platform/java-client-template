#!/usr/bin/env python3
"""
GitHub Actions Build Status Checker
Usage: python build_status.py [run_id|latest]
"""

import os
import sys
import json
import requests
import argparse
from datetime import datetime, timezone
from typing import Optional, Dict, Any

# Configuration
REPO = "Cyoda-platform/java-client-template"
WORKFLOW_FILE = "build.yml"

# Colors for terminal output
class Colors:
    RED = '\033[0;31m'
    GREEN = '\033[0;32m'
    YELLOW = '\033[1;33m'
    BLUE = '\033[0;34m'
    BOLD = '\033[1m'
    NC = '\033[0m'  # No Color

def get_github_token() -> str:
    """Get GitHub token from environment variable."""
    token = os.getenv('GITHUB_TOKEN')
    if not token:
        print(f"{Colors.RED}Error: GITHUB_TOKEN environment variable is not set{Colors.NC}")
        print("Please set your GitHub token: export GITHUB_TOKEN=your_token_here")
        sys.exit(1)
    return token

def make_github_request(url: str, token: str) -> Dict[Any, Any]:
    """Make authenticated request to GitHub API."""
    headers = {
        'Authorization': f'token {token}',
        'Accept': 'application/vnd.github.v3+json'
    }
    
    response = requests.get(url, headers=headers)
    
    if response.status_code == 404:
        print(f"{Colors.RED}Error: Resource not found (404){Colors.NC}")
        sys.exit(1)
    elif response.status_code != 200:
        print(f"{Colors.RED}Error: GitHub API request failed with status {response.status_code}{Colors.NC}")
        print(response.text)
        sys.exit(1)
    
    return response.json()

def get_latest_run_id(token: str) -> str:
    """Get the ID of the latest workflow run."""
    url = f"https://api.github.com/repos/{REPO}/actions/workflows/{WORKFLOW_FILE}/runs?per_page=1"
    data = make_github_request(url, token)
    
    if not data.get('workflow_runs'):
        print(f"{Colors.RED}No workflow runs found{Colors.NC}")
        sys.exit(1)
    
    return str(data['workflow_runs'][0]['id'])

def get_run_details(run_id: str, token: str) -> Dict[Any, Any]:
    """Get details for a specific workflow run."""
    url = f"https://api.github.com/repos/{REPO}/actions/runs/{run_id}"
    return make_github_request(url, token)

def get_status_emoji(status: str) -> str:
    """Get emoji for status."""
    status_map = {
        'success': '✅',
        'failure': '❌',
        'cancelled': '🚫',
        'in_progress': '🔄',
        'queued': '⏳',
        'pending': '⏳'
    }
    return status_map.get(status, '❓')

def format_duration(start_time: str, end_time: Optional[str] = None) -> str:
    """Format duration between start and end time."""
    start = datetime.fromisoformat(start_time.replace('Z', '+00:00'))
    
    if end_time:
        end = datetime.fromisoformat(end_time.replace('Z', '+00:00'))
    else:
        end = datetime.now(timezone.utc)
    
    duration = end - start
    total_seconds = int(duration.total_seconds())
    
    if total_seconds < 60:
        return f"{total_seconds}s"
    elif total_seconds < 3600:
        minutes = total_seconds // 60
        seconds = total_seconds % 60
        return f"{minutes}m {seconds}s"
    else:
        hours = total_seconds // 3600
        minutes = (total_seconds % 3600) // 60
        seconds = total_seconds % 60
        return f"{hours}h {minutes}m {seconds}s"

def display_run_status(run_data: Dict[Any, Any]) -> None:
    """Display formatted run status information."""
    run_id = run_data['id']
    status = run_data['status']
    conclusion = run_data.get('conclusion', 'in_progress')
    branch = run_data['head_branch']
    commit_sha = run_data['head_sha'][:7]
    actor = run_data['actor']['login']
    created_at = run_data['created_at']
    updated_at = run_data['updated_at']
    html_url = run_data['html_url']
    
    # Get build inputs if available
    build_type = "N/A"
    target_branch = "N/A"
    if 'inputs' in run_data and run_data['inputs']:
        build_type = run_data['inputs'].get('build_type', 'N/A')
        target_branch = run_data['inputs'].get('branch', 'N/A')
    
    duration = format_duration(created_at, updated_at if status == 'completed' else None)
    
    print(f"\n{Colors.BLUE}{Colors.BOLD}📊 GitHub Actions Build Status{Colors.NC}")
    print(f"{Colors.BLUE}{'=' * 35}{Colors.NC}")
    print(f"Run ID:       {Colors.YELLOW}{run_id}{Colors.NC}")
    print(f"Status:       {get_status_emoji(conclusion)} {conclusion.upper()}")
    print(f"Branch:       {Colors.GREEN}{branch}{Colors.NC}")
    print(f"Target:       {Colors.GREEN}{target_branch}{Colors.NC}")
    print(f"Build Type:   {Colors.YELLOW}{build_type}{Colors.NC}")
    print(f"Commit:       {Colors.YELLOW}{commit_sha}{Colors.NC}")
    print(f"Actor:        {Colors.GREEN}{actor}{Colors.NC}")
    print(f"Duration:     {duration}")
    print(f"URL:          {Colors.BLUE}{html_url}{Colors.NC}")
    
    # Status-specific messages
    if status == 'in_progress':
        print(f"\n{Colors.YELLOW}⏳ Build is still running...{Colors.NC}")
    elif conclusion == 'success':
        print(f"\n{Colors.GREEN}✅ Build completed successfully!{Colors.NC}")
    elif conclusion == 'failure':
        print(f"\n{Colors.RED}❌ Build failed. Check the logs for details.{Colors.NC}")

def trigger_workflow(branch: str, build_type: str, token: str) -> str:
    """Trigger a new workflow run and return the run ID."""
    import time

    # Get baseline run ID before triggering
    baseline_id = get_latest_run_id(token)

    # Trigger workflow
    url = f"https://api.github.com/repos/{REPO}/actions/workflows/{WORKFLOW_FILE}/dispatches"
    headers = {
        'Authorization': f'token {token}',
        'Accept': 'application/vnd.github.v3+json',
        'Content-Type': 'application/json'
    }

    payload = {
        'ref': branch,
        'inputs': {
            'branch': branch,
            'build_type': build_type
        }
    }

    response = requests.post(url, headers=headers, json=payload)

    if response.status_code != 204:
        print(f"{Colors.RED}Error triggering workflow: {response.status_code}{Colors.NC}")
        print(response.text)
        sys.exit(1)

    print(f"{Colors.GREEN}✅ Workflow triggered successfully!{Colors.NC}")
    print(f"Branch: {Colors.GREEN}{branch}{Colors.NC}")
    print(f"Build Type: {Colors.YELLOW}{build_type}{Colors.NC}")
    print(f"\n{Colors.BLUE}⏳ Waiting for workflow run to appear...{Colors.NC}")

    # Wait for new run to appear
    max_attempts = 12
    for attempt in range(1, max_attempts + 1):
        time.sleep(3)
        try:
            current_latest_id = get_latest_run_id(token)
            if current_latest_id != baseline_id:
                print(f"{Colors.GREEN}✅ Found new run ID: {current_latest_id}{Colors.NC}")
                return current_latest_id
        except:
            pass

        print(f"Attempt {attempt}/{max_attempts}: Waiting for run to appear...")

    print(f"{Colors.RED}Error: Timeout waiting for workflow run to appear{Colors.NC}")
    print(f"{Colors.BLUE}💡 Check manually: https://github.com/{REPO}/actions{Colors.NC}")
    sys.exit(1)

def main():
    parser = argparse.ArgumentParser(description='GitHub Actions Build Status Checker')
    parser.add_argument('action', nargs='?', default='latest', 
                       help='Action: run_id, "latest", or "trigger"')
    parser.add_argument('--branch', help='Branch name (for trigger action)')
    parser.add_argument('--build-type', default='standard',
                       choices=['compile-only', 'standard', 'workflow-import', 'both'],
                       help='Build type (for trigger action)')
    
    args = parser.parse_args()
    token = get_github_token()
    
    if args.action == 'trigger':
        if not args.branch:
            print(f"{Colors.RED}Error: --branch is required for trigger action{Colors.NC}")
            sys.exit(1)
        run_id = trigger_workflow(args.branch, args.build_type, token)
        print(f"\n{Colors.BLUE}📊 Getting initial status...{Colors.NC}")
        run_data = get_run_details(run_id, token)
        display_run_status(run_data)
        return
    
    # Get run ID
    if args.action == 'latest':
        print("Getting latest build status...")
        run_id = get_latest_run_id(token)
    else:
        run_id = args.action
    
    print(f"Fetching details for run ID: {run_id}")
    run_data = get_run_details(run_id, token)
    display_run_status(run_data)
    
    # Helpful tips
    if run_data['status'] == 'in_progress':
        print(f"\n{Colors.BLUE}💡 Tip: Re-run this script to check updated status{Colors.NC}")

if __name__ == '__main__':
    main()
